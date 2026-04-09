package org.example.controller;

import org.example.service.RagService;
import org.example.service.retrieval.RetrievedChunk;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本地 RAG 检索调试接口
 * 用于直接验证混合检索链路是否生效，不参与正式业务流程。
 */
@RestController
@RequestMapping("/api/rag")
public class RagDebugController {

    private final RagService ragService;

    public RagDebugController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/retrieve_debug")
    public List<RetrievedChunk> retrieveDebug(@RequestParam("q") String question) {
        return ragService.retrieveForDebug(question);
    }
}
