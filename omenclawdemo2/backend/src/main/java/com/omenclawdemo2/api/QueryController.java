package com.omenclawdemo2.api;

import com.omenclawdemo2.api.dto.QueryRequest;
import com.omenclawdemo2.api.dto.QueryResponse;
import com.omenclawdemo2.model.Message;
import com.omenclawdemo2.model.Run;
import com.omenclawdemo2.model.Session;
import com.omenclawdemo2.orchestrator.OrchestratorService;
import com.omenclawdemo2.repository.MessageRepository;
import com.omenclawdemo2.repository.RunRepository;
import com.omenclawdemo2.repository.SessionRepository;
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
            session = sessionRepository.findById(sid).orElseGet(() -> sessionRepository.save(new Session()));
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
        run.setPhase("planning");
        run = runRepository.save(run);

        orchestratorService.startRun(run.getId());

        return ResponseEntity.ok(new QueryResponse(session.getId(), run.getId()));
    }
}
