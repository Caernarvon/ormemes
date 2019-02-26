import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
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

public class Bot extends TelegramLongPollingBot implements Lock {
    private final String CHAT_ID = "-1001302700256";
    private final String CHANNEL_ID = "-1001441629708";

    private LinkedList<PollEntity> polls = Lists.newLinkedList();
    private Map<Integer, List<Update>> updateMap = Maps.newHashMap();

    public void onUpdateReceived(Update update) {
        addUpdate(update);
        if (!update.hasCallbackQuery() && findMultipleUpdates().isPresent()) {
            synchronized (this) {
                try {
                    this.tryLock(1, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getData().equals(CallbackData.VOTE.name()) &&
                !update.getCallbackQuery().getMessage().getText().contains(update.getCallbackQuery().getFrom().getFirstName())) {
                findPoll(update).ifPresent(poll -> editPoll(update, poll));
            }
        } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
            try {
                if (update.getMessage().hasPhoto()) {
                    synchronized (this) {
                        new Thread(() ->
                          findMultipleUpdates().ifPresent(multipleUpdates -> {
                            try {
                                execute(sendMediaGroup(multipleUpdates, CHAT_ID));
                                postPoll(createPoll(update, update.getMessage().getMessageId()));
                            } catch (TelegramApiException e) {
                                e.printStackTrace();
                            }
                          })
                        ).start();
                    }
                    //TODO научить реагировать на группы картинок и на одну

                } else {
                    ForwardMessage forwardMessage = new ForwardMessage();
                    forwardMessage.setChatId(CHAT_ID);
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

    private InlineKeyboardMarkup createPoll(Update update, Integer connectedPostId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowList = Lists.newArrayList();
        List<InlineKeyboardButton> keyboardButtonsRow1 = Lists.newArrayList();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText("Yes");
        yesButton.setCallbackData(CallbackData.VOTE.name());
        keyboardButtonsRow1.add(yesButton);

        rowList.add(keyboardButtonsRow1);
        inlineKeyboardMarkup.setKeyboard(rowList);

        PollEntity poll = PollEntity.builder()
            .chatId(update.getMessage().getChatId())
            .inlineKeyboardMarkup(inlineKeyboardMarkup)
            .connectedPostId(connectedPostId)
            .build();
        polls.add(poll);

        return inlineKeyboardMarkup;
    }

    private void postPoll(InlineKeyboardMarkup inlineKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(CHAT_ID).setText("За: ").setReplyMarkup(inlineKeyboardMarkup);
        try {
            polls.getLast().setMessageId(execute(sendMessage).getMessageId());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void editPoll(Update update, PollEntity poll) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(CHAT_ID);
        editMessage.setText(update.getCallbackQuery().getMessage().getText() + " "
            + update.getCallbackQuery().getFrom().getFirstName() + ", ");
        editMessage.setMessageId(poll.getMessageId());
        poll.setCounter(poll.getCounter() + 1);
        editMessage.setReplyMarkup(poll.getInlineKeyboardMarkup());
        poll.getInlineKeyboardMarkup().getKeyboard().get(0).get(0).setText("В прод  -  " + poll.getCounter().toString()); // отображение кол-ва проголосовавших
        try {
            GetChatMemberCount getChatMemberCount = new GetChatMemberCount();
            getChatMemberCount.setChatId(CHAT_ID);
            int membersCount = execute(getChatMemberCount) / 2;
            if (poll.getCounter() >= membersCount) {
                editMessage.setText("Пост отправлен");
                execute(editMessage);

                findUpdate(poll.getConnectedPostId()).ifPresent(upd -> {
                    try {
                        if (upd.getMessage().hasPhoto()) {

                            if (upd.getMessage().getPhoto().size() / 4 == 1) {
                                SendPhoto sendPhoto = new SendPhoto();
                                sendPhoto.setPhoto(upd.getMessage().getPhoto().get(0).getFileId());
                                sendPhoto.setCaption("прислал " + upd.getMessage().getFrom().getFirstName());
                                sendPhoto.setChatId(CHANNEL_ID);
                                execute(sendPhoto);

                                updateMap.remove(update.getMessage().getDate());
                                polls.remove(poll);
                            } else {
                              findMultipleUpdates().ifPresent(multipleUpdates -> {
                                try {
                                  execute(sendMediaGroup(multipleUpdates, CHANNEL_ID));

                                  updateMap.remove(update.getMessage().getDate());
                                  polls.remove(poll);
                                } catch (TelegramApiException e) {
                                  e.printStackTrace();
                                }
                              });
                            }

                        } else if (upd.getMessage().hasAnimation()) {

                        } else if (upd.getMessage().hasVideo()) {

                        }
                    }catch (TelegramApiException e){
                        e.printStackTrace();
                    }
                });
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
        List<InputMedia> inputMedia = Lists.newArrayList();

        filteredUpdates.forEach(update -> {
            InputMedia inputMediaPhoto = new InputMediaPhoto();
            inputMediaPhoto.setMedia(update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId());
            inputMedia.add(inputMediaPhoto);
            sendMediaGroup.setMedia(inputMedia);
            //TODO: сделать на видео, фото, анимации
            //TODO: вставлять имя отправителя и подписку под фото
        });
        return sendMediaGroup;
    }

    private Optional<Update> findUpdate(Integer messageId) {
        return updateMap.values().stream()
            .flatMap(Collection::stream)
            .filter(update -> (update.hasMessage() && update.getMessage().getMessageId().equals(messageId))
                || (update.hasCallbackQuery() && update.getCallbackQuery().getMessage().getMessageId().equals(messageId)))
            .findFirst();
    }

    private Optional<List<Update>> findMultipleUpdates() {
        return updateMap.values().stream()
            .filter(updates -> updates.size() > 1)
            .findFirst();
    }

    private Optional<PollEntity> findPoll(Update update) {
        return polls.stream()
            .filter(poll -> update.hasCallbackQuery()
                && update.getCallbackQuery().getMessage().getMessageId().equals(poll.getMessageId()))
            .findFirst();
    }

    private void addUpdate(Update update){
      Integer date = update.getMessage().getDate();
      if (!updateMap.containsKey(date)){
        updateMap.put(date, Lists.newArrayList(update));
      } else {
        updateMap.get(date).add(update);
      }
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
