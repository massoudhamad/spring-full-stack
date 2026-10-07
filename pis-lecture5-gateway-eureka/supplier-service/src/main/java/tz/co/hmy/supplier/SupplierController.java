package tz.co.hmy.supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository suppliers;

    public SupplierController(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    public List<Supplier> findAll() {
        return suppliers.findAll();
    }

    @GetMapping("/{id}")
    public Supplier findById(@PathVariable String id) {
        return suppliers.findById(id).orElseThrow(() -> new SupplierNotFoundException(id));
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail notFound(SupplierNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Supplier not found");
        return problem;
    }

    static class SupplierNotFoundException extends RuntimeException {
        SupplierNotFoundException(String id) { super("No supplier with id " + id); }
    }
}
