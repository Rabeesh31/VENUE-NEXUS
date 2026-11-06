/**
 * @venuenexus/api-client - Generated OpenAPI API Client placeholder
 * Generated/integrated in Phases 6-7 (Lackshana & Yuvan Nandan)
 */

export interface ApiClientConfig {
  baseUrl: string;
  getAuthToken?: () => string | null;
}

export class VenueNexusApiClient {
  constructor(private readonly config: ApiClientConfig) {}

  public getBaseUrl(): string {
    return this.config.baseUrl;
  }
}
