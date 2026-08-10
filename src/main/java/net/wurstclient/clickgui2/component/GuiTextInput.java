package net.wurstclient.clickgui2.component;

public interface GuiTextInput
{
	boolean acceptKey(int keyCode);

	void acceptChar(char codePoint);

	void loseFocus();
}
