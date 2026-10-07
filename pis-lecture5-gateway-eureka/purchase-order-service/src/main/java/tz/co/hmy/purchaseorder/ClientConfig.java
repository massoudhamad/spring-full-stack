package tz.co.hmy.purchaseorder;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

    /**
     * @LoadBalanced is the whole trick. Every RestClient built from this
     * builder treats the host in a URL as a SERVICE NAME: before sending, it
     * asks Eureka for the running copies of that service, picks one (round
     * robin), and swaps in its real host and port.
     */
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
