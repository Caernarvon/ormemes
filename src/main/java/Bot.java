import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMemberCount;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class Bot extends TelegramLongPollingBot implements Lock {

    private LinkedList<Poll> polls = new LinkedList<>();
    private LinkedList<Update> updates = new LinkedList<>();

    public void onUpdateReceived(Update update) {
        updates.add(update);
        if (!update.hasCallbackQuery() && updates.size() >= 2 && !(findMultipleUpdates().size() == 0)) {
            synchronized (this) {
                try {
                    this.tryLock(1, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getData().equals("vote") &&
                    !update.getCallbackQuery().getMessage().getText().contains(update.getCallbackQuery().getFrom().getFirstName())) {
                editPoll(update, (findPoll(update)));
            }
        } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
            try {
                if (update.getMessage().hasPhoto()) {
                    synchronized (this) {
                        new Thread(() -> {
                            try {
                                execute(sendMediaGroup(findMultipleUpdates(), "-1001302700256"));
                                postPoll(createPoll(update, update.getMessage().getMessageId()));
                            } catch (TelegramApiException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    //TODO научить реаггировать на группы картинок и на одну

                } else {
                    ForwardMessage forwardMessage = new ForwardMessage();
                    forwardMessage.setChatId("-1001302700256");
                    forwardMessage.setFromChatId(update.getMessage().getChatId());
                    forwardMessage.setMessageId(update.getMessage().getMessageId());
                    sendApiMethod(forwardMessage);
                    postPoll(createPoll(update, update.getMessage().getMessageId()));
                }
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private InlineKeyboardMarkup createPoll(Update update, Integer connectedPost) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        List<InlineKeyboardButton> keyboardButtonsRow1 = new LinkedList<>();
        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        Poll poll = new Poll();
        yesButton.setText("Yes");
        yesButton.setCallbackData("vote");
        keyboardButtonsRow1.add(yesButton);
        rowList.add(keyboardButtonsRow1);
        inlineKeyboardMarkup.setKeyboard(rowList);
        poll.setChatId(update.getMessage().getChatId());
        poll.setInlineKeyboardMarkup(inlineKeyboardMarkup);
        poll.setConnectedPost(connectedPost);
        polls.add(poll);
        return inlineKeyboardMarkup;
    }

    private void postPoll(InlineKeyboardMarkup inlineKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId("-1001302700256").setText("За: ").setReplyMarkup(inlineKeyboardMarkup);
        try {
            polls.getLast().setMessageId(execute(sendMessage).getMessageId());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void editPoll(Update update, Poll poll) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId("-1001302700256");
        editMessage.setText(update.getCallbackQuery().getMessage().getText() + " "
                + update.getCallbackQuery().getFrom().getFirstName() + ", ");
        editMessage.setMessageId(poll.getMessageId());
        poll.setCounter(poll.getCounter() + 1);
        editMessage.setReplyMarkup(poll.getInlineKeyboardMarkup());
        poll.getInlineKeyboardMarkup().getKeyboard().get(0).get(0).setText("В прод  -  " + poll.getCounter().toString()); // отображение кол-ва проголосовавших
        try {
            GetChatMemberCount getChatMemberCount = new GetChatMemberCount();
            getChatMemberCount.setChatId("-1001302700256");
            int membersCount = execute(getChatMemberCount) / 2;
            if (poll.getCounter() >= membersCount) {
                editMessage.setText("Пост отправлен");
                execute(editMessage);

                if (findUpdate(poll.getConnectedPost()).getMessage().hasPhoto()) {

                    if (findUpdate(poll.getConnectedPost()).getMessage().getPhoto().size() / 4 == 1) {
                        SendPhoto sendPhoto = new SendPhoto();
                        sendPhoto.setPhoto(findUpdate(poll.getConnectedPost()).getMessage().getPhoto().get(0).getFileId());
                        sendPhoto.setCaption("прислал " + findUpdate(poll.getConnectedPost()).getMessage().getFrom().getFirstName());
                        sendPhoto.setChatId("-1001441629708");
                        execute(sendPhoto);

                        updates.remove(update);
                        polls.remove(poll);
                    } else {
                        execute(sendMediaGroup(findMultipleUpdates(), "-1001441629708"));

                        updates.remove(update);
                        polls.remove(poll);
                    }

                } else if (findUpdate(poll.getMessageId()).getMessage().hasAnimation()) {

                } else if (findUpdate(poll.getMessageId()).getMessage().hasVideo()) {

                }
            } else {
                execute(editMessage);
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private SendMediaGroup sendMediaGroup(List<Update> filteredUpdates, String chatId) {
        SendMediaGroup sendMediaGroup = new SendMediaGroup();
        sendMediaGroup.setChatId(chatId);
        LinkedList<InputMedia> inputMedia = new LinkedList<>();

        for (Update update : filteredUpdates) {
            InputMedia inputMediaPhoto = new InputMediaPhoto();
            inputMediaPhoto.setMedia(update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId());
            inputMedia.add(inputMediaPhoto);
            sendMediaGroup.setMedia(inputMedia);
            //TODO: сделать на видео, фото, анимации
            //TODO: вставлять имя отправителя и подписку под фото
        }
        return sendMediaGroup;
    }

    private Update findUpdate(Integer messageId) {
        for (Update update : updates) {
            if (update.hasMessage() && update.getMessage().getMessageId().equals(messageId)) {
                return update;
            } else if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage().getMessageId().equals(messageId)) {
                return update;
            }
        }
        return null;
    }

    private ArrayList<Update> findMultipleUpdates() {
        ArrayList<Update> multipleUpdates = new ArrayList<>();
        if (updates.size() >= 2) {
            for (int i = 0; i < updates.size(); i++) {
                if (updates.get(i).getMessage().getDate().equals(updates.get(i + 1).getMessage().getDate())) {
                    multipleUpdates.add(updates.get(i));
                    if (!(updates.get(i + 1).equals(null)) && updates.get(i + 1).equals(updates.getLast())) {
                        multipleUpdates.add(updates.get(i + 1));
                        return multipleUpdates;
                    }
                }
            }
        }
        return null;
    }

    private Poll findPoll(Update update) {
        for (Poll poll : polls) {
            if (update.hasCallbackQuery() &&
                    update.getCallbackQuery().getMessage().getMessageId().equals(poll.getMessageId())) {
                return poll;
            }
        }
        return null;
    }

    public String getBotUsername() {
        return null;
    }

    public String getBotToken() {
        return "";
    }

    @Override
    public void lock() {

    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        return false;
    }

    @Override
    public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {

    }

    @NotNull
    @Override
    public Condition newCondition() {
        return null;
    }
}
