package com.rs.net.encoders.other;

import com.rs.utilities.TextUtils;
import com.rs.utilities.loaders.Censor;

public class ChatMessage {

	private String message;
	private String filteredMessage;

	public ChatMessage(String message) {
		if (!(this instanceof QuickChatMessage)) {
			filteredMessage = Censor.getFilteredMessage(message);
			this.message = TextUtils.fixChatMessage(message);
		} else
			this.message = message;
	}

	public String getMessage(boolean filtered) {
		return filtered ? filteredMessage : message;
	}
}
