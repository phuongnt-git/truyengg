package com.truyengg.security.qsc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QSCIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldGetQSCPublicKey() throws Exception {
    mockMvc.perform(get("/api/qsc/public-key"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.keyId").exists())
      .andExpect(jsonPath("$.data.algorithm").value("KYBER1024"))
      .andExpect(jsonPath("$.data.publicKey").exists());
  }

  @Test
  void shouldAccessAdminSettingsWithAuth() throws Exception {
    // Note: This test would need proper authentication setup
    // Placeholder for integration test structure
    mockMvc.perform(get("/api/admin/settings/tree"))
      .andExpect(status().is4xxClientError()); // Expect 401/403 without auth
  }
}

