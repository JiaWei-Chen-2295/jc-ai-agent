package fun.javierchen.jcaiagentbackend.controller;


import fun.javierchen.jcaiagentbackend.app.StudyFriend;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping(value = "/ai_friend")
@Tag(name = "学习伙伴聊天", description = "基于向量检索的 AI 学习助手对话接口（支持轮询与 SSE）")
public class StudyFriendController {

    @Resource
    private StudyFriend studyFriend;

    @GetMapping(value = "/do_chat/async", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(
            summary = "异步聊天（轮询获取完整回答）",
            description = "使用 chatId 维持上下文，会在服务端完成检索后返回一次性完整回答。",
            parameters = {
                    @Parameter(name = "chatMessage", description = "用户提问或对话内容", required = true, example = "什么是向量数据库？"),
                    @Parameter(name = "chatId", description = "会话唯一标识，复用以保持上下文", required = true, example = "session-001")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "AI 的完整回答", content = @Content(
                            mediaType = MediaType.TEXT_PLAIN_VALUE,
                            examples = @ExampleObject(value = "向量数据库用于存储和检索高维向量，常用于相似度搜索。")
                    ))
            }
    )
    public String doChatWithRAG(@RequestParam("chatMessage") String chatMessage,
                                @RequestParam("chatId") String chatId) {
        return studyFriend.doChatWithRAG(chatMessage, chatId);
    }

    @GetMapping(value = "/do_chat/sse/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "SSE 流式聊天",
            description = "以 text/event-stream 返回增量输出，前端可直接用 EventSource 订阅。每条 data 为模型的部分回答。",
            parameters = {
                    @Parameter(name = "chatMessage", description = "用户提问或对话内容", required = true, example = "请帮我总结这段话"),
                    @Parameter(name = "chatId", description = "会话唯一标识，复用以保持上下文", required = true, example = "session-001")
            }
    )
    @ApiResponse(responseCode = "200", description = "SSE 数据流（data 字段为字符串增量）", content = @Content(
            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            examples = @ExampleObject(value = "data: 正在为你整理要点\\n\\n")
    ))
    public SseEmitter doChatWithRAGStream(@RequestParam("chatMessage") String chatMessage,
                                          @RequestParam("chatId") String chatId) {
        SseEmitter sseEmitter = new SseEmitter(3 * 60 * 1000L);
        studyFriend.doChatWithRAGStream(chatMessage, chatId).subscribe(
                chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                },
                t -> {
                    log.error("出错{}", t.getMessage());
                    sseEmitter.complete();
                },
                sseEmitter::complete
        );
        return sseEmitter;
    }


    @GetMapping(value = "/do_chat/sse_with_tool/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "SSE 流式聊天（可触发工具调用）",
            description = "与上一个接口一致，但内部可调用工具以获取外部数据，返回的流中同样包含文本增量。",
            parameters = {
                    @Parameter(name = "chatMessage", description = "用户提问或对话内容", required = true, example = "帮我查询最新的课程表"),
                    @Parameter(name = "chatId", description = "会话唯一标识，复用以保持上下文", required = true, example = "session-001")
            }
    )
    @ApiResponse(responseCode = "200", description = "SSE 数据流（data 字段为字符串增量）", content = @Content(
            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            examples = @ExampleObject(value = "data: 我去调用工具查询...\\n\\n")
    ))
    public SseEmitter doChatWithRAGStreamTool(@RequestParam("chatMessage") String chatMessage,
                                              @RequestParam("chatId") String chatId) {
        SseEmitter sseEmitter = new SseEmitter(3 * 60 * 1000L);
        studyFriend.doChatWithRAGStreamTool(chatMessage, chatId).subscribe(
                chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                },
                t -> {
                    log.error("出错{}", t.getMessage());
                    sseEmitter.complete();
                },
                sseEmitter::complete
        );
        return sseEmitter;
    }
}
