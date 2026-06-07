package es.janrax.api_gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

	@GetMapping("/catalog")
	public Mono<Map<String, Object>> catalogFallback(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		return Mono.just(Map.of(
				"error", "Catalog service is temporarily unavailable",
				"status", 503
		));
	}

	@GetMapping("/queue")
	public Mono<Map<String, Object>> queueFallback(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		return Mono.just(Map.of(
				"error", "Queue service is temporarily unavailable",
				"status", 503
		));
	}

	@GetMapping("/reservation")
	public Mono<Map<String, Object>> reservationFallback(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		return Mono.just(Map.of(
				"error", "Reservation service is temporarily unavailable",
				"status", 503
		));
	}

	@GetMapping("/payment")
	public Mono<Map<String, Object>> paymentFallback(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		return Mono.just(Map.of(
				"error", "Payment service is temporarily unavailable",
				"status", 503
		));
	}
}
