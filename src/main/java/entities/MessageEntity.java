package entities;

import static constants.Properties.MESSAGE_TIMEOUT;
import static services.Bot.CURRENT_BOT;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import services.Bot;

@Data
@Slf4j
public class MessageEntity {

  public static Map<Integer, MessageEntity> messageMap;

  private Integer date;

  private List<Update> updateList;

  private Boolean complete = false;

  private MessageEntity(Integer date, Update update){
    this.date = date;
    updateList = Lists.newArrayList(update);
    new Thread(() ->{
      try {
        Thread.sleep(MESSAGE_TIMEOUT);
        CURRENT_BOT.sendMessage(messageMap.get(date));
        complete = true;
      } catch (InterruptedException | TelegramApiException e) {
        e.printStackTrace();
      }
    }).start();
  }

  public static void newEntity(Update update){
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
