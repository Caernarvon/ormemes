package entities;

import static constants.Properties.*;
import static services.Bot.CURRENT_BOT;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Maps;
import lombok.Data;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Data
public class MessageEntity {

    public static Map<Integer, MessageEntity> messageMap = Maps.newHashMap();

    private Integer date;

    private List<Update> updateList;

    private Boolean complete = false;

    private boolean sentByAdmin;

    private MessageEntity(Integer date, Update update) {
        this.date = date;
        updateList = Lists.newArrayList(update);
        try {
            GetChatAdministrators getChatAdministrators = new GetChatAdministrators()
                    .setChatId(CHAT_ID);
            sentByAdmin = CURRENT_BOT.execute(getChatAdministrators).stream().findFirst().get().getUser().getId()
                    .equals(update.getMessage().getFrom().getId());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        new Thread(() -> {
            try {
                Thread.sleep(MESSAGE_TIMEOUT);
                CURRENT_BOT.sendMessage(messageMap.get(date));
                complete = true;
            } catch (InterruptedException | TelegramApiException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void newEntity(Update update) {
        if (Objects.nonNull(update.getMessage())) {
            Integer date = update.getMessage().getDate();
            if (messageMap.containsKey(date)) {
                messageMap.get(date).getUpdateList().add(update);
            } else {
                messageMap.put(date, new MessageEntity(date, update));
            }
        }
    }

}
