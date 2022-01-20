package springbootstartergraphql.springbootstartergraphql;

import graphql.schema.idl.RuntimeWiring;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Component
class CrmRunTimeWiringConfigurer implements RuntimeWiringConfigurer{
    private final CrmClient crmClient;

    CrmRunTimeWiringConfigurer(CrmClient crmClient) {
        this.crmClient = crmClient;
    }

    @Override
    public void configure(RuntimeWiring.Builder builder) {
        builder.type("Query",b->b.dataFetcher("customers", env->crmClient.getCustomers()));
    }
//    verbose version
//    @Override
//    public void configure(RuntimeWiring.Builder builder) {
//        builder.type("Query",new UnaryOperator<TypeRuntimeWiring.Builder>(){
//            @Override
//            public TypeRuntimeWiring.Builder apply(TypeRuntimeWiring.Builder builder) {
//                return builder.dataFetcher("customers", new DataFetcher() {
//                    @Override
//                    public Object get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {
//                        return crmClient.getCustomers();
//                    }
//                });
//            }
//        });
//    }
}

@Component
class CrmClient {
    private final Map<Customer, Collection<Order>> db = new ConcurrentHashMap<>();
    private final AtomicInteger id = new AtomicInteger();

    CrmClient() {
        Flux.fromIterable(List.of("Naveen", "Rahul", "Neo", "Kundan"))
                .flatMap(this::addCustomer)
                .subscribe(customer -> {
                    var list = this.db.get(customer);
                    for (var orderId = 1; orderId <= Math.random() * 100; orderId++) {
                        list.add(new Order(orderId, customer.getId()));
                    }
                });
    }

    Flux<Order> getOrdersFor(Integer customerId) {
        return getCustomerById(customerId)
                .map(this.db::get)
                .flatMapMany(Flux::fromIterable);
    }

    Flux<Customer> getCustomers() {
        return Flux.fromIterable(this.db.keySet());
    }

    Flux<Customer> getCustomerByName(String name) {
        return getCustomers().filter(customer -> customer.getName().equalsIgnoreCase(name));
    }

    Mono<Customer> addCustomer(String name) {
        var key = new Customer(id(), name);
        this.db.put(key, new CopyOnWriteArrayList<>());
        return Mono.just(key);
    }

    Mono<Customer> getCustomerById(Integer customerId) {
        return getCustomers().filter(customer -> customer.getId().equals(customerId)).singleOrEmpty();
    }

    Flux<CustomerEvent> getCustomerEvents(Integer customerId) {
        return getCustomerById(customerId)
                .flatMapMany(customer -> {
                    return Flux.fromStream(
                            Stream.generate(() -> {
                                var event = Math.random() > .5 ? CustomerEventType.CREATED : CustomerEventType.UPDATED;
                                return new CustomerEvent(customer, event);
                            })
                    );
                }).take(10)
                .delayElements(Duration.ofSeconds(1));
    }

    private int id() {
        return this.id.getAndIncrement();
    }
}
@Data
@AllArgsConstructor
@NoArgsConstructor
class CustomerEvent{
    private Customer customer;
    private CustomerEventType event;
}

enum CustomerEventType {
    CREATED, UPDATED
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Order{
    private Integer id;
    private Integer customerId;
}
@Data
@AllArgsConstructor
@NoArgsConstructor
class Customer{
    private Integer id;
    private String name;
}
