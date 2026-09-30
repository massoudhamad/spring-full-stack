package tz.co.hmy.pis.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.repository.RequisitionRepository;

import java.util.UUID;

/**
 * Rules that depend on the DATA, not just on who the user is.
 *
 * Called from @PreAuthorize as  @requisitionGuard.raisedBy(#id, authentication.name).
 * "raisedBy" means created_by (Lesson 2): the login that created the record,
 * not the requestedBy text the client typed in.
 */
@Component("requisitionGuard")
@RequiredArgsConstructor
public class RequisitionGuard {

    private final RequisitionRepository requisitions;

    @Transactional(readOnly = true)
    public boolean raisedBy(UUID requisitionId, String username) {
        // An unknown id is "not raised by you": the service then answers 404, not 403.
        return requisitions.findById(requisitionId)
                .map(requisition -> username.equals(requisition.getCreatedBy()))
                .orElse(false);
    }
}
