package net.wurstclient.command;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstTranslator;
import net.wurstclient.commands.AddAltCmd;

final class CommandTest
{
	@Test
	void syntaxIsTranslatedAtDisplayTimeInsteadOfDuringStartup()
		throws Exception
	{
		Field field = WurstClient.class.getDeclaredField("translator");
		field.setAccessible(true);
		Object previous = field.get(WurstClient.INSTANCE);
		try
		{
			field.set(WurstClient.INSTANCE, null);
			assertDoesNotThrow(AddAltCmd::new);
			String[] input = {".example", ".example alternate"};
			Command command = new Command("example", "Description", input)
			{
				@Override
				public void call(String[] args) {}
			};
			assertEquals(".example", input[0]);
			input[0] = "changed";
			String[] prefix = {"Syntax: "};
			field.set(WurstClient.INSTANCE, new WurstTranslator()
			{
				@Override
				public String translate(String key, Object... args)
				{
					assertEquals("Syntax: %s", key);
					return prefix[0] + args[0];
				}
			});
			assertArrayEquals(new String[]{"Syntax: .example",
				".example alternate"}, command.getSyntax());
			command.getSyntax()[0] = "changed";
			prefix[0] = "用法：";
			assertEquals("用法：.example", command.getSyntax()[0]);
			assertEquals("Description\n\n用法：.example\n.example alternate",
				command.getDescription());
		}finally
		{
			field.set(WurstClient.INSTANCE, previous);
		}
	}
}
