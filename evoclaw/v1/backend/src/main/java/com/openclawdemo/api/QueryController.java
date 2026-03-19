package com.openclawdemo.api;

import com.openclawdemo.api.dto.QueryRequest;
import com.openclawdemo.api.dto.QueryResponse;
import com.openclawdemo.model.Message;
import com.openclawdemo.model.Run;
import com.openclawdemo.model.Session;
import com.openclawdemo.orchestrator.OrchestratorService;
import com.openclawdemo.repository.MessageRepository;
import com.openclawdemo.repository.RunRepository;
import com.openclawdemo.repository.SessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final RunRepository runRepository;
    private final OrchestratorService orchestratorService;

    public QueryController(SessionRepository sessionRepository,
                           MessageRepository messageRepository,
                           RunRepository runRepository,
                           OrchestratorService orchestratorService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> submitQuery(@RequestBody QueryRequest request) {
        Session session;
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            Long sid = Long.parseLong(request.getSessionId());
            session = sessionRepository.findById(sid)
                    .orElseGet(() -> sessionRepository.save(new Session()));
        } else {
            session = sessionRepository.save(new Session());
        }

        Message message = new Message();
        message.setSession(session);
        message.setRole("user");
        message.setContent(request.getMessage());
        message = messageRepository.save(message);

        Run run = new Run();
        run.setSession(session);
        run.setRootMessage(message);
        run.setStatus("running");
        run = runRepository.save(run);

        orchestratorService.startRun(run.getId());

        return ResponseEntity.ok(new QueryResponse(session.getId(), run.getId()));
    }
}

