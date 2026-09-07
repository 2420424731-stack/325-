package com.family.finance;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.family.finance.mapper")
public class FamilyFinanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FamilyFinanceApplication.class, args);
	}

}
