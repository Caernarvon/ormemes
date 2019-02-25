import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public class Poll {
    private Integer messageId;
    private long chatId;
    private Integer connectedPost;
    private InlineKeyboardMarkup inlineKeyboardMarkup;
    private Integer counter;

    Poll() {
        this.counter = 0;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public long getChatId() {
        return chatId;
    }

    void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public InlineKeyboardMarkup getInlineKeyboardMarkup() {
        return inlineKeyboardMarkup;
    }

    public void setInlineKeyboardMarkup(InlineKeyboardMarkup inlineKeyboardMarkup) {
        this.inlineKeyboardMarkup = inlineKeyboardMarkup;
    }

    public Integer getCounter() {
        return counter;
    }

    public void setCounter(Integer counter) {
        this.counter = counter;
    }

    public Integer getConnectedPost() {
        return connectedPost;
    }

    public void setConnectedPost(Integer connectedPost) {
        this.connectedPost = connectedPost;
    }
}
