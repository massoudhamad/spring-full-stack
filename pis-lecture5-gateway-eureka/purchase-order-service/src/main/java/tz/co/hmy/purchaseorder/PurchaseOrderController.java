package tz.co.hmy.purchaseorder;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService orders;

    public PurchaseOrderController(PurchaseOrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<PurchaseOrder> create(@Valid @RequestBody PurchaseOrderRequest request,
                                                UriComponentsBuilder uri) {
        PurchaseOrder created = orders.create(request);
        return ResponseEntity.created(uri.path("/api/purchase-orders/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    public List<PurchaseOrder> findAll() {
        return orders.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrder> findById(@PathVariable String id) {
        return ResponseEntity.of(orders.findById(id));
    }

    @ExceptionHandler(PurchaseOrderService.BusinessRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ProblemDetail businessRule(PurchaseOrderService.BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", ex.getMessage());
    }

    /** The other service is down. Say so honestly: 503, not 500, and not a made-up answer. */
    @ExceptionHandler(SupplierClient.SupplierServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ProblemDetail supplierServiceDown(SupplierClient.SupplierServiceUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Supplier service unavailable",
                "Suppliers can't be checked right now. Try again shortly.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalid(MethodArgumentNotValidException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
