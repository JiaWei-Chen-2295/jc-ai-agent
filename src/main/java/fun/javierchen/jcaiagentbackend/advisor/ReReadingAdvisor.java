package fun.javierchen.jcaiagentbackend.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

	private ChatClientRequest before(ChatClientRequest chatClientRequest) {
		Prompt augmentedPrompt = chatClientRequest.prompt()
			.augmentUserMessage(userMessage -> {
				String originalText = userMessage.getText();
				return userMessage.mutate()
					.text(originalText + "\nRead the question again: " + originalText)
					.build();
			});
		return chatClientRequest.mutate().prompt(augmentedPrompt).build();
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
		return chain.nextCall(this.before(chatClientRequest));
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
		return chain.nextStream(this.before(chatClientRequest));
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}
}