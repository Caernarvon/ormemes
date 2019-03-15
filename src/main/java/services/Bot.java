package services;

import static constants.Properties.BOT_TOKEN;
import static constants.Properties.CHANNEL_ID;
import static constants.Properties.CHAT_ID;

import com.google.common.collect.Lists;
import entities.MessageEntity;
import entities.PollEntity;
import constants.CallbackData;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMemberCount;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Bot extends TelegramLongPollingBot {

    public static Bot CURRENT_BOT;

    public Bot() {
        CURRENT_BOT = this;
    }

    private LinkedList<PollEntity> polls = Lists.newLinkedList();

    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            //Poll click
            if (update.getCallbackQuery().getData().contains(CallbackData.VOTE.name()) &&
                    !update.getCallbackQuery().getMessage().getText().contains(update.getCallbackQuery().getFrom().getFirstName())) {
                findPoll(update).ifPresent(poll -> editPoll(update, poll));
            }
        } else if (update.hasMessage()) {
            //Message received
            if (update.getMessage().hasPhoto() || update.getMessage().hasDocument() || update.getMessage().hasVideo()) {
                MessageEntity.newEntity(update);
                sendMessage("Принято на рассмотрение", update);
            } else {
                sendMessage("Произошла ошибка", update);
            }
        } else {
            sendMessage("Произошла ошибка", update);
        }
    }


    private InlineKeyboardMarkup createPoll(Update update, Integer connectedPostId, boolean sentByAdmin) {
        InlineKeyboardButton yesButton = new InlineKeyboardButton()
                .setText("На канал  -  " + (sentByAdmin ? 1 : 0))
                .setCallbackData(CallbackData.VOTE.name() + ", " + connectedPostId);

        List<InlineKeyboardButton> keyboardButtonsRow = Lists.newArrayList();
        keyboardButtonsRow.add(yesButton);

        List<List<InlineKeyboardButton>> rowList = Lists.newArrayList();
        rowList.add(keyboardButtonsRow);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup()
                .setKeyboard(rowList);


        PollEntity poll = PollEntity.builder()
                .chatId(update.getMessage().getChatId())
                .inlineKeyboardMarkup(inlineKeyboardMarkup)
                .connectedPostId(connectedPostId)
                .counter(sentByAdmin ? 1 : 0)
                .build();
        polls.add(poll);

        return inlineKeyboardMarkup;
    }

    private void postPoll(InlineKeyboardMarkup inlineKeyboardMarkup, String username) {
        SendMessage sendMessage = new SendMessage()
                .setChatId(CHAT_ID)
                .setText("За: ")
                .setReplyMarkup(inlineKeyboardMarkup);
        if (username != null) {
            sendMessage.setText("За: " + username);
        }
        try {
            polls.getLast().setMessageId(execute(sendMessage).getMessageId());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void editPoll(Update update, PollEntity poll) {
        poll.setCounter(poll.getCounter() + 1);
        poll.getInlineKeyboardMarkup().getKeyboard().get(0).get(0).setText("На канал  -  " + poll.getCounter().toString()); // отображение кол-ва проголосовавших

        EditMessageText editMessage = new EditMessageText()
                .setChatId(CHAT_ID)
                .setMessageId(poll.getMessageId())
                .setReplyMarkup(poll.getInlineKeyboardMarkup())
                .setText(update.getCallbackQuery().getMessage().getText() + generateName(update));

        try {
            GetChatMemberCount getChatMemberCount = new GetChatMemberCount().setChatId(CHAT_ID);
            int membersCount = execute(getChatMemberCount) / 2;
            if (poll.getCounter() >= membersCount) {
                findUpdate(poll.getConnectedPostId()).ifPresent(upd -> {
                    try {
                        if (upd.getMessage().hasPhoto()) {
                            if (!findMultipleUpdates().isPresent()) {
                                sendPhoto(upd, poll);
                                polls.remove(poll);
                            } else {
                                sendMultipleMedia(poll);
                            }
                        } else if (upd.getMessage().hasDocument()) {
                            sendDocument(upd, poll);
                            polls.remove(poll);
                        } else if (upd.getMessage().hasVideo()) {
                            if (!findMultipleUpdates().isPresent()) {
                                sendVideo(upd, poll);
                                polls.remove(poll);
                            } else {
                                sendMultipleMedia(poll);
                            }
                        }
                        editMessage.setText("Пост на канале");
                        execute(editMessage);
                    } catch (TelegramApiException e) {
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

    public void sendMessage(MessageEntity messageEntity) throws TelegramApiException {
        List<Update> updateList = messageEntity.getUpdateList();
        if (updateList.get(0).getMessage().hasDocument()) {
            sendDocument(updateList.get(0), null);
        } else {
            execute(sendMediaGroup(updateList, CHAT_ID));
        }
        if (messageEntity.isSentByAdmin()) {
            postPoll(createPoll(updateList.get(0), updateList.get(0).getMessage().getMessageId(), messageEntity.isSentByAdmin()),
                    generateName(messageEntity.getUpdateList().get(0)));
        } else {
            postPoll(createPoll(updateList.get(0), updateList.get(0).getMessage().getMessageId(), messageEntity.isSentByAdmin()), null);
        }
    }

    private void sendMessage(String text, Update update) {
        SendMessage sendMessage = new SendMessage()
                .setText(text)
                .setChatId(update.getMessage().getChatId());
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String generateName(Update update) {
        if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getFrom().getUserName() != null) {
                return "@" + update.getCallbackQuery().getFrom().getUserName();
            } else {
                return update.getCallbackQuery().getFrom().getFirstName();
            }
        } else {
            if (update.getMessage().getFrom().getUserName() != null) {
                return "@" + update.getMessage().getFrom().getUserName();
            } else {
                return update.getMessage().getFrom().getFirstName();
            }
        }
    }

    private void sendPhoto(Update update, PollEntity poll) throws TelegramApiException {
        SendPhoto sendPhoto = new SendPhoto()
                .setPhoto(update.getMessage().getPhoto().get(0).getFileId())
                .setCaption("прислал(а) " + generateName(update) + " через @ormemes_bot")
                .setChatId(CHANNEL_ID);
        execute(sendPhoto);
        findUpdate(poll.getConnectedPostId()).ifPresent(foundUpdate -> {
            MessageEntity.messageMap.remove(foundUpdate.getMessage().getDate());
        });
    }

    private void sendVideo(Update update, PollEntity poll) throws TelegramApiException {
        if (!findMultipleUpdates().isPresent()) {
            SendVideo sendVideo = new SendVideo()
                    .setVideo(update.getMessage().getVideo().getFileId())
                    .setCaption("прислал(а) " + update.getMessage().getFrom().getFirstName() + " через @ormemes_bot")
                    .setChatId(CHANNEL_ID);
            execute(sendVideo);
            findUpdate(poll.getConnectedPostId()).ifPresent(foundUpdate -> {
                MessageEntity.messageMap.remove(foundUpdate.getMessage().getDate());
            });
        }
    }

    private void sendDocument(Update update, PollEntity poll) throws TelegramApiException {
        if (!findMultipleUpdates().isPresent() && poll != null) {
            SendDocument sendDocument = new SendDocument()
                    .setDocument(update.getMessage().getDocument().getFileId())
                    .setCaption("прислал(а) " + update.getMessage().getFrom().getFirstName() + " через @ormemes_bot")
                    .setChatId(CHANNEL_ID);
            execute(sendDocument);
            findUpdate(poll.getConnectedPostId()).ifPresent(foundUpdate -> {
                MessageEntity.messageMap.remove(foundUpdate.getMessage().getDate());
            });
        } else {

            SendDocument sendDocument = new SendDocument()
                    .setDocument(update.getMessage().getDocument().getFileId())
                    .setChatId(CHAT_ID);
            execute(sendDocument);
        }
    }


    private SendMediaGroup sendMediaGroup(List<Update> filteredUpdates, String chatId) {
        SendMediaGroup sendMediaGroup = new SendMediaGroup()
                .setChatId(chatId);
        List<InputMedia> inputMediaList = Lists.newArrayList();

        filteredUpdates.forEach(update -> {
            InputMedia inputMedia;
            if (update.getMessage().hasPhoto()) {
                inputMedia = new InputMediaPhoto()
                        .setMedia(update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId());
            } else {
                inputMedia = new InputMediaVideo()
                        .setMedia(update.getMessage().getVideo().getFileId());
            }
            inputMediaList.add(inputMedia);
            sendMediaGroup.setMedia(inputMediaList);
        });
        return sendMediaGroup;
    }

    private void sendMultipleMedia(PollEntity poll) {
        findMultipleUpdates().ifPresent(multipleUpdates -> {
            try {

                execute(sendMediaGroup(multipleUpdates, CHANNEL_ID));
                findUpdate(poll.getConnectedPostId()).ifPresent(foundUpdate -> {
                    MessageEntity.messageMap.remove(foundUpdate.getMessage().getDate());
                });
                polls.remove(poll);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        });
    }

    private Optional<Update> findUpdate(Integer messageId) {
        return MessageEntity.messageMap.values().stream()
                .flatMap(message -> message.getUpdateList().stream())
                .filter(update -> (update.hasMessage() && update.getMessage().getMessageId().equals(messageId))
                        || (update.hasCallbackQuery() && update.getCallbackQuery().getMessage().getMessageId().equals(messageId)))
                .findFirst();
    }

    private Optional<List<Update>> findMultipleUpdates() {
        return MessageEntity.messageMap.values().stream()
                .map(MessageEntity::getUpdateList)
                .filter(updateList -> updateList.size() > 1)
                .findFirst();
    }

    private Optional<PollEntity> findPoll(Update update) {
        return polls.stream()
                .filter(poll -> update.hasCallbackQuery()
                        && update.getCallbackQuery().getMessage().getMessageId().equals(poll.getMessageId()))
                .findFirst();
    }

    public String getBotUsername() {
        return "ormemes";
    }

    public String getBotToken() {
        return BOT_TOKEN;
    }
}
