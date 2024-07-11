package com.reto.interbank.ms_anti_fraud.listener;

import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Slf4j
@Component
public class KafkaFraudListener {

	private final ObjectMapper objectMapper;
    private final KafkaReceiver<String, String> kafkaReceiver;
    private final KafkaSender<String, String> kafkaSender;
    private final String topicUpdateTransactionStatus = "topicUpdateTransactionStatus";
    
    
    public KafkaFraudListener(KafkaReceiver<String, String> kafkaReceiver,
            ObjectMapper objectMapper, KafkaSender<String, String> kafkaSender) {
    	this.kafkaReceiver = kafkaReceiver;
    	this.objectMapper = objectMapper;
    	this.kafkaSender = kafkaSender;
    }
    
    @PostConstruct
    public void startConsumeTopic() {
    	consumeTopics();
    }
    
    private void consumeTopics() {
        kafkaReceiver.receive()
            .doOnNext(record -> {
                String topic = record.topic();
                String value = record.value();
                log.info("Received message from topic {}: {}", topic, value);

                if ("topicSendFraud".equals(topic)) {
                	consumeTopicSendFraud(value);
                }
            })
            .subscribe();
    }
    
	public void consumeTopicSendFraud(String message){
	    log.info("Inicio de consumo de topic topicSendFraud");
        	JsonNode jsonNode;
			try {
				jsonNode = objectMapper.readTree(message);
				UUID transactionId = UUID.fromString(jsonNode.get("transactionId").asText());
	            Double value = jsonNode.get("value").asDouble();
	            String status = value > 1000 ? "RECHAZADO" : "APROBADO";
	            log.info("transactionId: {}, estado final: {}", transactionId, status);
	            
	            sendStatusToTransaction(transactionId, status);
	            log.info("Finalizando enviado a topicSendFraud");
			} catch (JsonMappingException e) {
				log.error("Error de mapeo JSON", e);
			} catch (JsonProcessingException e) {
				log.error("Error de procesamiento JSON", e);
			}
    }
	
    public void sendStatusToTransaction(UUID transactionId, String status) {
        String message = "{\"transactionId\": \"" + transactionId 
                + "\", \"status\": \"" + status + "\"}";
        log.info("Enviando mensaje a topicUpdateTransactionStatus: {}", message);
                
        kafkaSender.send(Mono.just(SenderRecord.create(
            new ProducerRecord<>(topicUpdateTransactionStatus, message), null))).subscribe();
    }

}