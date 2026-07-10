import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestRegex {
    public static void main(String[] args) {
        Pattern CITY_PATTERN = Pattern.compile(
            "(?:weather\\s+)?(?:in|for|at)\\s+([A-Za-z][A-Za-z\\s]{1,30}?)\\s*" +
                    "(?:\\?|$|right now|today|currently|now|tonight|at the moment|forecast|like\\??|for\\s+(?:the\\s+)?(?:next|today|tonight))",
            Pattern.CASE_INSENSITIVE);
            
        String input = "What is the weather in Hanoi for the next 6 hours?";
        Matcher m = CITY_PATTERN.matcher(input);
        if (m.find()) {
            System.out.println("Match found! City: '" + m.group(1) + "'");
        } else {
            System.out.println("No match");
        }
    }
}