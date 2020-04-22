package max.lab.rst.s03;

import lombok.RequiredArgsConstructor;
import max.lab.rst.domain.Book;
import max.lab.rst.domain.InMemoryDataSource;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@RequiredArgsConstructor
@Configuration
public class C03RouterBased {
    private static final String PATH_PREFIX = "/routed/";

    private final Validator validator;

    @Bean
    public RouterFunction<ServerResponse> routers() {
        return RouterFunctions.route()
                .POST(PATH_PREFIX + "book", this::create)
                .GET(PATH_PREFIX + "books", this::findAll)
                .GET(PATH_PREFIX + "book/{isbn}", this::find)
                .PUT(PATH_PREFIX + "book/{isbn}", this::update)
                .DELETE(PATH_PREFIX + "book/{isbn}", this::delete)
                .build();
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        var isbn = request.pathVariable("isbn");
        return InMemoryDataSource.findBookMonoById(isbn)
                .flatMap(book -> {
                    InMemoryDataSource.removeBook(book);
                    return ServerResponse.ok().build();
                }).switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var isbn = request.pathVariable("isbn");
        return InMemoryDataSource.findBookMonoById(isbn)
                .flatMap(book ->
                        C04ReactiveControllerHelper
                                .requestBodyToMono(request, validator, Book.class)
                                .map(InMemoryDataSource::saveBook)
                                .flatMap(b -> ServerResponse.ok().build())
                ).switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> find(ServerRequest request) {
        var isbn = request.pathVariable("isbn");
        return InMemoryDataSource.findBookMonoById(isbn)
                .flatMap(book -> ServerResponse.ok().bodyValue(book))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> findAll(ServerRequest request) {
        var books = InMemoryDataSource.findAllBooks();
        return ServerResponse.ok().bodyValue(books);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return C04ReactiveControllerHelper.requestBodyToMono(request, validator,
                (t, errors) -> InMemoryDataSource.findBookMonoById(t.getIsbn())
                            .map((book -> {
                                errors.rejectValue("isbn", "already.exists", "Already exists");
                                return Tuples.of(book, errors);
                            }))
//                (t, errors) -> {
//                    Optional<Book> theBook = InMemoryDataSource.findBookById(t.getIsbn());
//                    if (theBook.isPresent()) {
//                        errors.rejectValue("isbn", "already.exists", "Already exists");
//                    }
//                    return Tuples.of(t, errors);
//                }
                , Book.class)
                .map(InMemoryDataSource::saveBook)
                .flatMap(book -> ServerResponse.created(
                    UriComponentsBuilder.fromHttpRequest(request.exchange().getRequest())
                            .path("/").path(book.getIsbn()).build().toUri())
                        .build());
    }
}