import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
class PollEntity {

    private Integer messageId;

    private Long chatId;

    private Integer connectedPostId;

    private InlineKeyboardMarkup inlineKeyboardMarkup;

    private Integer counter;
}
