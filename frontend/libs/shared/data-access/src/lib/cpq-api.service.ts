import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApprovalRequestDto,
  ApprovalResponseRequest,
  ApprovalSubmitRequest,
  BulkPricingSimulationRequest,
  BulkPricingSimulationResponse,
  ConfigurationSessionDto,
  ConfigurationStatus,
  CpqPageRequest,
  DocumentGenerationJobDto,
  DocumentJobStatus,
  LineItemCreateRequest,
  PriceBookCreateRequest,
  PriceBookDto,
  PriceBookEntryCreateRequest,
  PriceBookEntryDto,
  PriceBookEntryUpdateRequest,
  PriceBookUpdateRequest,
  PricingAuditLogDto,
  PricingContext,
  PricingResult,
  ProductCategory,
  ProductCreateRequest,
  ProductDto,
  ProductStatus,
  ProductType,
  ProductUpdateRequest,
  QuoteCreateRequest,
  QuoteDealType,
  QuoteDto,
  QuoteLineItemDto,
  QuoteStatus,
  QuoteUpdateRequest,
  SpringPageResponse,
} from '@frontend/shared-models';

@Injectable({ providedIn: 'root' })
export class CpqApiService {
  private readonly http = inject(HttpClient);

  listProducts(params?: CpqPageRequest & {
    productType?: ProductType;
    category?: ProductCategory;
    status?: ProductStatus;
  }): Observable<SpringPageResponse<ProductDto>> {
    return this.http.get<SpringPageResponse<ProductDto>>('/api/v1/products', {
      params: this.toHttpParams(params),
    });
  }

  searchProducts(q: string, params?: CpqPageRequest): Observable<SpringPageResponse<ProductDto>> {
    return this.http.get<SpringPageResponse<ProductDto>>('/api/v1/products/search', {
      params: this.toHttpParams({ ...params, q }),
    });
  }

  getProduct(id: number): Observable<ProductDto> {
    return this.http.get<ProductDto>(`/api/v1/products/${id}`);
  }

  getProductBySku(sku: string): Observable<ProductDto> {
    return this.http.get<ProductDto>(`/api/v1/products/sku/${encodeURIComponent(sku)}`);
  }

  createProduct(request: ProductCreateRequest): Observable<ProductDto> {
    return this.http.post<ProductDto>('/api/v1/products', request);
  }

  updateProduct(id: number, request: ProductUpdateRequest): Observable<ProductDto> {
    return this.http.put<ProductDto>(`/api/v1/products/${id}`, request);
  }

  activateProduct(id: number): Observable<ProductDto> {
    return this.http.post<ProductDto>(`/api/v1/products/${id}/activate`, {});
  }

  deprecateProduct(id: number): Observable<ProductDto> {
    return this.http.post<ProductDto>(`/api/v1/products/${id}/deprecate`, {});
  }

  retireProduct(id: number, reason: string): Observable<ProductDto> {
    return this.http.post<ProductDto>(`/api/v1/products/${id}/retire`, {}, {
      params: this.toHttpParams({ reason }),
    });
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/products/${id}`);
  }

  listPriceBooks(params?: CpqPageRequest & { region?: string }): Observable<SpringPageResponse<PriceBookDto>> {
    return this.http.get<SpringPageResponse<PriceBookDto>>('/api/v1/price-books', {
      params: this.toHttpParams(params),
    });
  }

  getPriceBook(id: number): Observable<PriceBookDto> {
    return this.http.get<PriceBookDto>(`/api/v1/price-books/${id}`);
  }

  createPriceBook(request: PriceBookCreateRequest): Observable<PriceBookDto> {
    return this.http.post<PriceBookDto>('/api/v1/price-books', request);
  }

  updatePriceBook(id: number, request: PriceBookUpdateRequest): Observable<PriceBookDto> {
    return this.http.put<PriceBookDto>(`/api/v1/price-books/${id}`, request);
  }

  deletePriceBook(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/price-books/${id}`);
  }

  listPriceBookEntries(priceBookId: number, params?: CpqPageRequest): Observable<SpringPageResponse<PriceBookEntryDto>> {
    return this.http.get<SpringPageResponse<PriceBookEntryDto>>(`/api/v1/price-books/${priceBookId}/entries`, {
      params: this.toHttpParams(params),
    });
  }

  getPriceBookEntry(priceBookId: number, entryId: number): Observable<PriceBookEntryDto> {
    return this.http.get<PriceBookEntryDto>(`/api/v1/price-books/${priceBookId}/entries/${entryId}`);
  }

  createPriceBookEntry(priceBookId: number, request: PriceBookEntryCreateRequest): Observable<PriceBookEntryDto> {
    return this.http.post<PriceBookEntryDto>(`/api/v1/price-books/${priceBookId}/entries`, request);
  }

  updatePriceBookEntry(
    priceBookId: number,
    entryId: number,
    request: PriceBookEntryUpdateRequest
  ): Observable<PriceBookEntryDto> {
    return this.http.put<PriceBookEntryDto>(`/api/v1/price-books/${priceBookId}/entries/${entryId}`, request);
  }

  deletePriceBookEntry(priceBookId: number, entryId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/price-books/${priceBookId}/entries/${entryId}`);
  }

  executePricing(context: PricingContext): Observable<PricingResult> {
    return this.http.post<PricingResult>('/api/v1/pricing/execute', context);
  }

  simulatePricing(context: PricingContext): Observable<PricingResult> {
    return this.http.post<PricingResult>('/api/v1/pricing/simulate', context);
  }

  simulatePricingBulk(request: BulkPricingSimulationRequest): Observable<BulkPricingSimulationResponse> {
    return this.http.post<BulkPricingSimulationResponse>('/api/v1/pricing/simulate/bulk', request);
  }

  getPricingAuditTrail(quoteExternalId: string): Observable<PricingAuditLogDto[]> {
    return this.http.get<PricingAuditLogDto[]>(`/api/v1/pricing/audit/${encodeURIComponent(quoteExternalId)}`);
  }

  listQuotes(params?: CpqPageRequest & {
    status?: QuoteStatus;
    dealType?: QuoteDealType;
    customerExternalId?: string;
  }): Observable<SpringPageResponse<QuoteDto>> {
    return this.http.get<SpringPageResponse<QuoteDto>>('/api/v1/quotes', {
      params: this.toHttpParams(params),
    });
  }

  getQuote(id: number): Observable<QuoteDto> {
    return this.http.get<QuoteDto>(`/api/v1/quotes/${id}`);
  }

  getQuoteByNumber(quoteNumber: string): Observable<QuoteDto> {
    return this.http.get<QuoteDto>(`/api/v1/quotes/number/${encodeURIComponent(quoteNumber)}`);
  }

  createQuote(request: QuoteCreateRequest): Observable<QuoteDto> {
    return this.http.post<QuoteDto>('/api/v1/quotes', request);
  }

  updateQuote(id: number, request: QuoteUpdateRequest): Observable<QuoteDto> {
    return this.http.put<QuoteDto>(`/api/v1/quotes/${id}`, request);
  }

  submitQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/submit`, {});
  }

  approveQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/approve`, {});
  }

  rejectQuote(id: number, reason?: string): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/reject`, {}, {
      params: this.toHttpParams({ reason }),
    });
  }

  sendQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/send`, {});
  }

  acceptQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/accept`, {});
  }

  expireQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/expire`, {});
  }

  cancelQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/cancel`, {});
  }

  cloneQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/clone`, {});
  }

  calculateQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`/api/v1/quotes/${id}/calculate`, {});
  }

  deleteQuote(id: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/quotes/${id}`);
  }

  listQuoteLineItems(quoteId: number): Observable<QuoteLineItemDto[]> {
    return this.http.get<QuoteLineItemDto[]>(`/api/v1/quotes/${quoteId}/line-items`);
  }

  addQuoteLineItem(quoteId: number, request: LineItemCreateRequest): Observable<QuoteLineItemDto> {
    return this.http.post<QuoteLineItemDto>(`/api/v1/quotes/${quoteId}/line-items`, request);
  }

  updateQuoteLineItem(
    quoteId: number,
    lineItemId: number,
    request: LineItemCreateRequest
  ): Observable<QuoteLineItemDto> {
    return this.http.put<QuoteLineItemDto>(`/api/v1/quotes/${quoteId}/line-items/${lineItemId}`, request);
  }

  removeQuoteLineItem(quoteId: number, lineItemId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/quotes/${quoteId}/line-items/${lineItemId}`);
  }

  submitQuoteForApproval(quoteId: number, request: ApprovalSubmitRequest): Observable<ApprovalRequestDto> {
    return this.http.post<ApprovalRequestDto>(`/api/v1/quotes/${quoteId}/approvals`, request);
  }

  getQuoteApprovals(quoteId: number): Observable<ApprovalRequestDto[]> {
    return this.http.get<ApprovalRequestDto[]>(`/api/v1/quotes/${quoteId}/approvals`);
  }

  getPendingApprovals(): Observable<ApprovalRequestDto[]> {
    return this.http.get<ApprovalRequestDto[]>('/api/v1/quotes/approvals/pending');
  }

  approveApprovalRequest(approvalId: number, request: ApprovalResponseRequest): Observable<ApprovalRequestDto> {
    return this.http.post<ApprovalRequestDto>(`/api/v1/quotes/approvals/${approvalId}/approve`, request);
  }

  rejectApprovalRequest(approvalId: number, request: ApprovalResponseRequest): Observable<ApprovalRequestDto> {
    return this.http.post<ApprovalRequestDto>(`/api/v1/quotes/approvals/${approvalId}/reject`, request);
  }

  createConfiguration(productId: string, userId: string): Observable<ConfigurationSessionDto> {
    return this.http.post<ConfigurationSessionDto>('/api/v1/configurations', {}, {
      params: this.toHttpParams({ productId, userId }),
    });
  }

  getConfiguration(id: number): Observable<ConfigurationSessionDto> {
    return this.http.get<ConfigurationSessionDto>(`/api/v1/configurations/${id}`);
  }

  listConfigurations(userId: string, status?: ConfigurationStatus): Observable<ConfigurationSessionDto[]> {
    return this.http.get<ConfigurationSessionDto[]>('/api/v1/configurations', {
      params: this.toHttpParams({ userId, status }),
    });
  }

  addConfigurationSelection(id: number, optionKey: string, optionValue: string): Observable<ConfigurationSessionDto> {
    return this.http.post<ConfigurationSessionDto>(`/api/v1/configurations/${id}/selections`, {}, {
      params: this.toHttpParams({ optionKey, optionValue }),
    });
  }

  removeConfigurationSelection(id: number, optionKey: string): Observable<ConfigurationSessionDto> {
    return this.http.delete<ConfigurationSessionDto>(
      `/api/v1/configurations/${id}/selections/${encodeURIComponent(optionKey)}`
    );
  }

  validateConfiguration(id: number): Observable<ConfigurationSessionDto> {
    return this.http.post<ConfigurationSessionDto>(`/api/v1/configurations/${id}/validate`, {});
  }

  completeConfiguration(id: number): Observable<ConfigurationSessionDto> {
    return this.http.post<ConfigurationSessionDto>(`/api/v1/configurations/${id}/complete`, {});
  }

  cancelConfiguration(id: number): Observable<ConfigurationSessionDto> {
    return this.http.post<ConfigurationSessionDto>(`/api/v1/configurations/${id}/cancel`, {});
  }

  getDocumentJob(id: number): Observable<DocumentGenerationJobDto> {
    return this.http.get<DocumentGenerationJobDto>(`/api/v1/documents/jobs/${id}`);
  }

  getDocumentJobsByQuote(quoteId: string): Observable<DocumentGenerationJobDto[]> {
    return this.http.get<DocumentGenerationJobDto[]>(`/api/v1/documents/jobs/quote/${encodeURIComponent(quoteId)}`);
  }

  getDocumentJobsByStatus(status: DocumentJobStatus): Observable<DocumentGenerationJobDto[]> {
    return this.http.get<DocumentGenerationJobDto[]>(`/api/v1/documents/jobs/status/${status}`);
  }

  retryDocumentJob(id: number): Observable<DocumentGenerationJobDto> {
    return this.http.post<DocumentGenerationJobDto>(`/api/v1/documents/jobs/${id}/retry`, {});
  }

  private toHttpParams(params?: CpqPageRequest): HttpParams {
    let httpParams = new HttpParams();

    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });

    return httpParams;
  }
}
