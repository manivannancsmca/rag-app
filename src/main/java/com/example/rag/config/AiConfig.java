package com.example.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    // ── ChatClient with default system persona ────────────────
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        You are a precise document assistant. You answer questions
                        STRICTLY based on the document context provided to you.

                        Rules:
                        1. Use ONLY the information in the provided context.
                        2. If the context does not contain enough information,
                           state that clearly — do not fabricate.
                        3. Cite the source document and page number when possible.
                        4. Be concise but thorough.
                        """)
                .build();
    }

    
    // ── Token-aware text splitter ─────────────────────────────
    //
    //  FIX: Replaced the 5-parameter constructor that doesn't exist
    //  in Spring AI 2.0.0 with the no-arg constructor.
    //
    //  Internally it uses these defaults (verified from source):
    //    chunkSize            = 800
    //    minChunkSizeChars    = 350
    //    minChunkLengthTokens = 10
    //    maxNumChunks         = 10000
    //    keepSeparator        = true
    //
    //  These match exactly what the application.yml expects.

    @Bean
    TokenTextSplitter textSplitter() {
        TokenTextSplitter splitter = new TokenTextSplitter();
        log.info("Configured TokenTextSplitter — defaults: "
                + "chunkSize=800, minChars=350, minTokens=10, maxChunks=10000");
        return splitter;
    }

    // ── Thread pool for async ingestion ───────────────────────

    @Bean("ingestionExecutor")
    Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ingestion-");
        executor.initialize();
        log.info("Ingestion thread pool: core=2, max=4, queue=20");
        return executor;
    }
    
}
