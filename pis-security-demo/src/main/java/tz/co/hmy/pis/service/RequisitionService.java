package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.dto.*;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.Requisition;
import tz.co.hmy.pis.model.RequisitionItem;
import tz.co.hmy.pis.model.RequisitionStatus;
import tz.co.hmy.pis.repository.RequisitionRepository;

import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class RequisitionService {

    private final RequisitionRepository requisitions;
    private final ReferenceGenerator references;

    @Transactional
    public RequisitionResponse create(RequisitionRequest request) {
        Requisition requisition = new Requisition(
                references.nextRequisitionReference(),
                request.department(), request.requestedBy(),
                request.justification(), request.requiredByDate());

        request.items().forEach(i -> requisition.addItem(toItem(i)));

        return RequisitionResponse.from(requisitions.save(requisition));
    }

    public PageResponse<RequisitionResponse> findAll(RequisitionStatus status,
                                                     String department,
                                                     Pageable pageable) {
        return PageResponse.from(
                requisitions.search(status, department, pageable).map(RequisitionResponse::from));
    }

    public RequisitionResponse findById(UUID id) {
        return RequisitionResponse.from(getWithItems(id));
    }

    /**
     * Replaces the item list wholesale. orphanRemoval deletes the rows that are
     * no longer in the collection, so there is no separate delete call.
     */
    @Transactional
    public RequisitionResponse update(UUID id, RequisitionRequest request) {
        Requisition requisition = getWithItems(id);
        requireEditable(requisition);

        requisition.update(request.department(), request.requestedBy(),
                request.justification(), request.requiredByDate());

        requisition.clearItems();
        request.items().forEach(i -> requisition.addItem(toItem(i)));

        return RequisitionResponse.from(requisition);
    }

    @Transactional
    public RequisitionResponse submit(UUID id) {
        Requisition requisition = getWithItems(id);
        if (requisition.getStatus() != RequisitionStatus.DRAFT) {
            throw new BusinessRuleException("Only a DRAFT requisition can be submitted");
        }
        requisition.submit();
        return RequisitionResponse.from(requisition);
    }

    /**
     * Two checks before the method runs: the permission, and separation of
     * duties. Whoever raised a requisition may not approve it, even an admin
     * who holds every permission.
     */
    @PreAuthorize("hasAuthority('requisition:approve') and !@requisitionGuard.raisedBy(#id, authentication.name)")
    @Transactional
    public RequisitionResponse approve(UUID id) {
        Requisition requisition = getWithItems(id);
        if (requisition.getStatus() != RequisitionStatus.SUBMITTED) {
            throw new BusinessRuleException("Only a SUBMITTED requisition can be approved");
        }
        requisition.approve();
        return RequisitionResponse.from(requisition);
    }

    @PreAuthorize("hasAuthority('requisition:approve') and !@requisitionGuard.raisedBy(#id, authentication.name)")
    @Transactional
    public RequisitionResponse reject(UUID id) {
        Requisition requisition = getWithItems(id);
        if (requisition.getStatus() != RequisitionStatus.SUBMITTED) {
            throw new BusinessRuleException("Only a SUBMITTED requisition can be rejected");
        }
        requisition.reject();
        return RequisitionResponse.from(requisition);
    }

    @Transactional
    public void delete(UUID id) {
        Requisition requisition = getWithItems(id);
        requireEditable(requisition);
        requisitions.delete(requisition);
    }

    private static RequisitionItem toItem(RequisitionItemRequest r) {
        return new RequisitionItem(r.itemCode(), r.description(), r.quantity(),
                r.unit(), r.estimatedUnitPrice());
    }

    private void requireEditable(Requisition requisition) {
        if (requisition.getStatus() != RequisitionStatus.DRAFT) {
            throw new BusinessRuleException(
                    "A requisition in status " + requisition.getStatus() + " can no longer be changed");
        }
    }

    private Requisition getWithItems(UUID id) {
        return requisitions.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Requisition", id));
    }
}
