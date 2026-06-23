package com.example.demo.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // 동시성 문제를 해결하기 위해 ArrayList 대신 CopyOnWriteArrayList를 사용합니다.
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println(" 새 세션 연결 성공! 현재 세션 수: " + sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("📩 서버가 받은 데이터: " + payload);

        //  연결된 모든 세션에 에러 없이 안전하게 브로드캐스팅합니다.
        for (WebSocketSession s : sessions) {
            if (s != null && s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(payload));
                } catch (Exception e) {
                    System.out.println(" 메시지 전송 중 제외 발생: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println(" 세션 연결 종료. 현재 세션 수: " + sessions.size());
    }
}