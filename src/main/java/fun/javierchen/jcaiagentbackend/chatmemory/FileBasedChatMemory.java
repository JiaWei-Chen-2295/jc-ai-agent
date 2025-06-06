package fun.javierchen.jcaiagentbackend.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;

    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        // 设置实例化策略
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public FileBasedChatMemory(String baseDir) {
        BASE_DIR = baseDir;
        // 创建目录
        File baseDirFile = new File(BASE_DIR);
        if (!baseDirFile.exists()) {
            baseDirFile.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 获取之前的消息 加上新增的消息
        List<Message> oldMessages = getOrCreateConversation(conversationId);
        oldMessages.addAll(messages);
        saveConversation(conversationId, oldMessages);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<Message> messages = getOrCreateConversation(conversationId);
        // 获取最后N条消息
        return messages.subList(Math.max(messages.size() - lastN, 0), messages.size());
    }

    @Override
    public void clear(String conversationId) {
        // 清空会话信息
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 保存 AI 会话信息
     * @param conversationId
     * @param messages
     */
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file), 1024 * 1024 * 1000)) {
            kryo.writeObject(output, messages);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取或创建会话信息的列表
     * @param conversationId
     * @return
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file), 1024 * 1024 * 1000)) {
                messages = kryo.readObject(input, ArrayList.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return messages;
    }

    /**
     * 获取当前会话文件
     * @param conversationId
     * @return
     */
    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR + File.separator + conversationId + ".kryo");
    }
}
