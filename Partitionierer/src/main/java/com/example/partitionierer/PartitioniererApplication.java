package com.example.partitionierer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PartitioniererApplication {

    public static void main(String[] args) {

        System.exit(SpringApplication.exit(SpringApplication.run(PartitioniererApplication.class, args)));
    }

}
