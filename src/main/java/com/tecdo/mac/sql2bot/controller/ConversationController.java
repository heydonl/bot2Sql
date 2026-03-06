package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.Conversation;
import com.tecdo.mac.sql2bot.domain.Message;
import com.tecdo.mac.sql2bot.service.ConversationService;
import com.tecdo.mac.sql2bot.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * 创建新会话
     */
    @PostMapping
    public Result<Conversation> create(@RequestParam Long userId,
                                      @RequestParam(required = false) String title,
                                      @RequestParam(required = false) Long datasourceId) {
        try {
            Conversation conversation = conversationService.create(userId, title, datasourceId);
            return Result.success(conversation);
        } catch (Exception e) {
            log.error("Failed to create conversation", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询用户的所有会话
     */
    @GetMapping("/user/{userId}")
    public Result<List<Conversation>> listByUserId(@PathVariable Long userId) {
        try {
            List<Conversation> conversations = conversationService.listByUserId(userId);
            return Result.success(conversations);
        } catch (Exception e) {
            log.error("Failed to list conversations", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询会话详情
     */
    @GetMapping("/{id}")
    public Result<Conversation> getById(@PathVariable Long id) {
        try {
            Conversation conversation = conversationService.getById(id);
            if (conversation == null) {
                return Result.error(404, "Conversation not found");
            }
            return Result.success(conversation);
        } catch (Exception e) {
            log.error("Failed to get conversation", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询会话的所有消息
     */
    @GetMapping("/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long id) {
        try {
            List<Message> messages = messageService.listByConversationId(id);
            return Result.success(messages);
        } catch (Exception e) {
            log.error("Failed to get messages", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/{id}/title")
    public Result<Void> updateTitle(@PathVariable Long id, @RequestParam String title) {
        try {
            conversationService.updateTitle(id, title);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to update conversation title", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam Long userId) {
        try {
            // 验证会话是否属于该用户
            if (!conversationService.belongsToUser(id, userId)) {
                return Result.error(403, "无权删除该会话");
            }

            // 删除会话的所有消息
            messageService.deleteByConversationId(id);

            // 删除会话
            conversationService.delete(id);

            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete conversation", e);
            return Result.error(e.getMessage());
        }
    }
}
