package com.unidev;

import com.unidev.polyinsights.Application;
import com.unidev.polyinsights.model.InsightType;
import com.unidev.polyinsights.model.Tenant;
import com.unidev.polyinsights.service.TenantDAO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Application.class, PolyInsightsApplicationTests.class})
@SpringBootConfiguration
public class PolyInsightsApplicationTests {

	@Autowired
	private TenantDAO tenantDAO;

	@Test
	public void contextLoads() {

	}

	@Test
	public void tenantCrud() {

		Tenant tenant = new Tenant();
		tenant.setTenant("potato");

		InsightType insightType = new InsightType();
		insightType.setInterval(1000);
		insightType.setName("Test");
		insightType.setValues(new HashSet<>(Arrays.asList("1", "2", "3")));
		tenant.addType(insightType);

		tenantDAO.save(tenant);

		Tenant dbTenant = tenantDAO.findOne("potato");
		assertThat(dbTenant, is(notNullValue()));
		assertThat(dbTenant.getTenant(), is("potato"));
		assertThat(dbTenant.getTypes(), is(notNullValue()));
		assertThat(dbTenant.getTypes().size(), is(1));
		assertThat(dbTenant.getTypes().get("Test"), is(notNullValue()));
		assertThat(dbTenant.getTypes().get("test").getName(), is("Test"));

	}



}
