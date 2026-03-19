package com.openclaw3.api;

import com.openclaw3.api.dto.QueryRequest;
import com.openclaw3.api.dto.QueryResponse;
import com.openclaw3.model.Message;
import com.openclaw3.model.Run;
import com.openclaw3.model.Session;
import com.openclaw3.orchestrator.OrchestratorService;
import com.openclaw3.repository.MessageRepository;
import com.openclaw3.repository.RunRepository;
import com.openclaw3.repository.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<?> submitQuery(@RequestBody QueryRequest request) {
        Session session;
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            Long sid = Long.parseLong(request.getSessionId());
            session = sessionRepository.findById(sid).orElseGet(() -> sessionRepository.save(new Session()));
        } else {
            session = sessionRepository.save(new Session());
        }

        List<Run> activeRuns = runRepository.findBySessionAndPhaseIn(session, List.of("planning", "executing"));
        if (!activeRuns.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "A run is already active for this session. Please wait for it to finish.",
                            "activeRunId", activeRuns.get(0).getId(),
                            "sessionId", session.getId()
                    ));
        }

        Message message = new Message();
        message.setSession(session);
        message.setRole("user");
        message.setContent(request.getMessage());
        message = messageRepository.save(message);

        Run run = new Run();
        run.setSession(session);
        run.setRootMessage(message);
        run.setPhase("planning");
        run = runRepository.save(run);

        orchestratorService.startRun(run.getId());

        return ResponseEntity.ok(new QueryResponse(session.getId(), run.getId()));
    }
}
