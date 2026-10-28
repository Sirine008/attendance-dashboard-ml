package tn.attijari.assiduite.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tn.attijari.assiduite.domain.AuditLog;
import tn.attijari.assiduite.repository.AuditLogRepository;

/** Traçabilité (loi organique 2004-63) : consultations de fiches individuelles et actions d'administration. */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, String action, String entityType, Object entityId, String details) {
        AuditLog a = new AuditLog();
        a.setUsername(username);
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId != null ? entityId.toString() : null);
        a.setDetails(details != null && details.length() > 500 ? details.substring(0, 500) : details);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String fwd = req.getHeader("X-Forwarded-For");
            a.setIpAddress(fwd != null ? fwd.split(",")[0].trim() : req.getRemoteAddr());
        }
        repository.save(a);
    }
}
