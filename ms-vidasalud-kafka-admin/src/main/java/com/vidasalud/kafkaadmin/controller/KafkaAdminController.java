package com.vidasalud.kafkaadmin.controller;

import com.vidasalud.kafkaadmin.dto.ClusterInfoResponse;
import com.vidasalud.kafkaadmin.dto.ConsumerGroupResponse;
import com.vidasalud.kafkaadmin.dto.ConsumerGroupSummary;
import com.vidasalud.kafkaadmin.dto.PartitionsRequest;
import com.vidasalud.kafkaadmin.dto.TopicConfigRequest;
import com.vidasalud.kafkaadmin.dto.TopicInfoResponse;
import com.vidasalud.kafkaadmin.dto.TopicRequest;
import com.vidasalud.kafkaadmin.dto.TopicSummary;
import com.vidasalud.kafkaadmin.service.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API para administrar Kafka (solo rol Admin). El controller NO tiene lógica:
 * valida el request con @Valid y delega todo en KafkaAdminService.
 */
@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    // ---------- Cluster ----------

    @GetMapping("/cluster")
    public ClusterInfoResponse cluster() {
        return service.cluster();
    }

    // ---------- Topics ----------

    @PostMapping("/topics")
    public ResponseEntity<TopicSummary> crearTopic(@Valid @RequestBody TopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearTopic(request));
    }

    @GetMapping("/topics")
    public List<TopicSummary> listarTopics() {
        return service.listarTopics();
    }

    @GetMapping("/topics/{name}")
    public TopicInfoResponse obtenerTopic(@PathVariable String name) {
        return service.obtenerTopic(name);
    }

    @PutMapping("/topics/{name}/config")
    public TopicInfoResponse actualizarConfig(@PathVariable String name,
                                              @Valid @RequestBody TopicConfigRequest request) {
        return service.actualizarConfig(name, request.configs());
    }

    @PutMapping("/topics/{name}/partitions")
    public TopicSummary aumentarParticiones(@PathVariable String name,
                                            @Valid @RequestBody PartitionsRequest request) {
        return service.aumentarParticiones(name, request.totalPartitions());
    }

    @DeleteMapping("/topics/{name}")
    public ResponseEntity<Void> eliminarTopic(@PathVariable String name,
                                              @RequestParam(defaultValue = "false") boolean force) {
        service.eliminarTopic(name, force);
        return ResponseEntity.noContent().build();
    }

    // ---------- Consumer groups ----------

    @GetMapping("/consumer-groups")
    public List<ConsumerGroupSummary> listarGrupos() {
        return service.listarGrupos();
    }

    @GetMapping("/consumer-groups/{groupId}")
    public ConsumerGroupResponse obtenerGrupo(@PathVariable String groupId) {
        return service.obtenerGrupo(groupId);
    }
}
