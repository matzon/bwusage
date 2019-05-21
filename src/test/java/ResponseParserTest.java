import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

/**
 * Created by Brian Matzon <brian@matzon.dk>.
 */
public class ResponseParserTest {

    @Test
    void testParsing() {

        String input = "     {\"2019-05-21\":{\"down\":\"21.7 GB\",\"up\":\"730 MB\"},\"2019-05-20\":{\"down\":\"16.2 GB\",\"up\":\"456 MB\"},\"2019-05-19\":{\"down\":\"80 GB\",\"up\":\"1.68 GB\"},\"2019-05-18\":{\"down\":\"21.3 GB\",\"up\":\"560 MB\"},\"2019-05-17\":{\"down\":\"17.7 GB\",\"up\":\"1.77 GB\"},\"2019-05-16\":{\"down\":\"19.6 GB\",\"up\":\"636 MB\"},\"2019-05-15\":{\"down\":\"18.1 GB\",\"up\":\"717 MB\"}}";
        JsonElement parse = new JsonParser().parse(input);

        JsonObject jsonObject = parse.getAsJsonObject();
        Set<Map.Entry<String, JsonElement>> entries = jsonObject.entrySet();
        for (Map.Entry<String, JsonElement> entry : entries) {
            System.out.println("Down: " + entry.getValue().getAsJsonObject().get("down").getAsString());
            System.out.println("Up: " + entry.getValue().getAsJsonObject().get("up").getAsString());
        }
    }
}
