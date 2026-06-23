package com.example.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpSession;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Controller
public class ChatController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<Map<String, Object>> getParticipantsList() {
        try {
            InputStream inputStream = new ClassPathResource("data/attendance.json").getInputStream();
            Map<String, Object> dbData = objectMapper.readValue(inputStream, Map.class);
            return (List<Map<String, Object>>) dbData.get("participants");
        } catch (Exception e) {
            System.out.println("⚠️ 데이터 로드 실패: " + e.getMessage());
            return null;
        }
    }

    // 누구나 접근 가능한 영역 (Public)
    @GetMapping("/main") public String mainPage() { return "main"; }
    @GetMapping("/step1") public String step1Page() { return "step1"; }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (session.getAttribute("loginName") != null) { return "redirect:/ex"; }
        return "login";
    }

    @PostMapping("/login")
    public String loginProcess(@RequestParam("username") String username,
                               @RequestParam("password") String password,
                               HttpSession session, Model model) {
        try {
            InputStream inputStream = new ClassPathResource("data/manager.json").getInputStream();
            Map<String, Object> dbData = objectMapper.readValue(inputStream, Map.class);
            List<Map<String, Object>> managers = (List<Map<String, Object>>) dbData.get("managers");

            boolean isAuthenticated = false;
            Map<String, Object> loggedInManager = null;

            if (managers != null) {
                for (Map<String, Object> manager : managers) {
                    if (username.equals(manager.get("username")) && password.equals(manager.get("password"))) {
                        isAuthenticated = true;
                        loggedInManager = manager;
                        break;
                    }
                }
            }

            if (isAuthenticated) {
                session.setAttribute("loginName", loggedInManager.get("name"));
                session.setAttribute("loginRole", loggedInManager.get("role"));
                
                // 관계자 연락처 보존 (예: 010-5751-9060)
                session.setAttribute("loginPhone", loggedInManager.get("phone"));
                return "redirect:/ex";
            } else {
                model.addAttribute("error", "아이디 또는 비밀번호가 일치하지 않습니다.");
                return "login";
            }
        } catch (Exception e) {
            model.addAttribute("error", "서버 시스템 오류가 발생했습니다.");
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login"; 
    }

    // 보안 영역 (Private)
    @GetMapping("/ex")
    public String chatPage(HttpSession session, Model model) {
        if (session.getAttribute("loginName") == null) { return "redirect:/login"; }
        List<Map<String, Object>> participants = getParticipantsList();
        if (participants != null) {
            participants.sort((p1, p2) -> {
                String g1 = (String) p1.get("grade");
                String g2 = (String) p2.get("grade");
                if ("VIP".equals(g1) && !"VIP".equals(g2)) return -1;
                if (!"VIP".equals(g1) && "VIP".equals(g2)) return 1;
                return 0;
            });
            model.addAttribute("participants", participants);
        }
        return "ex";
    }

    @GetMapping("/qr")
    public String qrPage(HttpSession session, Model model) {
        if (session.getAttribute("loginName") == null) { return "redirect:/login"; }
        model.addAttribute("participants", getParticipantsList());
        return "qr"; 
    }

    // 실시간 카카오톡 발송 통제 API
    @PostMapping("/api/notify-manager")
    @ResponseBody
    public String notifyKakao(@RequestParam("name") String name, @RequestParam("grade") String grade, HttpSession session) {
        String loginPhone = (String) session.getAttribute("loginPhone");
        
        if ("VIP".equals(grade) && loginPhone != null) {
            String targetPhone = loginPhone.replace("-", "").trim();
            
            // 💡 세션 로그인 관계자의 번호가 네 번호와 일치하는지 최종 검증
            if ("01057519060".equals(targetPhone)) {
                try {
                    // 🚨 깨끗하게 특수 공백 세척 완료한 실제 토큰 주입
                    String kakaoToken = "7WHqJtSnI9Yl2XMQetygvnK9rd2Z3WoqAAAAAQoNFKMAAAGe8kqmDJCBbdpZdq0Z"; 
                    
                    String url = "https://kapi.kakao.com/v2/api/talk/memo/default/send";
                    RestTemplate restTemplate = new RestTemplate();

                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    // .trim()을 붙여서 문자열 앞뒤 혹시 모를 개행이나 공백을 완전 격리
                    headers.add("Authorization", "Bearer " + kakaoToken.trim());
                    headers.add("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");

                    String messageTemplate = "{"
                            + "\"object_type\": \"text\","
                            + "\"text\": \"🚨 [VIP 입장 완료]\\n" + name + " 귀빈께서 현장에 출석하셨습니다.\","
                            + "\"link\": {"
                            + "    \"web_url\": \"https://developers.kakao.com\","
                            + "    \"mobile_web_url\": \"https://developers.kakao.com\""
                            + "}"
                            + "}";

                    org.springframework.util.MultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
                    params.add("template_object", messageTemplate);

                    org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new org.springframework.http.HttpEntity<>(params, headers);

                    restTemplate.postForEntity(url, request, String.class);
                    System.out.println(" 관계자 인증 성공 및 카톡 직통 발송 완료!");
                    return "SUCCESS";
                } catch(Exception e) {
                    System.out.println(" 카톡 전송 실패: " + e.getMessage());
                    return "FAIL";
                }
            } else {
                System.out.println(" 로그인 번호 일치 안 함 (현재 세션 번호: " + targetPhone + ")");
                return "SKIPPED_PHONE_MISMATCH";
            }
        }
        return "SKIPPED";
    }
}