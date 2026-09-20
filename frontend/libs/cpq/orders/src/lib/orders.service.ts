import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  OrderPage,
  OrderView,
  OrderTask,
  OrderTimeline,
  OrderException,
  SourceReference,
  Command,
  OperationStatus,
} from './orders.model';

/** Uses the shell's ApiResponse interceptor, including its Spring-page pagination projection. */
@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/orders';
  list(
    page: number,
    filters: {
      q?: string;
      status?: string;
      customerId?: string;
      externalReference?: string;
      hasExceptions?: string;
    } = {},
  ) {
    let params = new HttpParams().set('page', page).set('size', 20);
    for (const [key, value] of Object.entries(filters))
      if (value?.trim()) params = params.set(key, value.trim());
    return this.http.get<OrderPage<OrderView>>(this.base, { params });
  }
  detail(id: string) {
    return this.http.get<OrderView>(`${this.base}/${encodeURIComponent(id)}`);
  }
  operation(kind: 'order' | 'exception', id: string, key: string) {
    const base = kind === 'order' ? this.base : '/api/v1/order-exceptions';
    return this.http.get<OperationStatus>(
      `${base}/${encodeURIComponent(id)}/operations/${encodeURIComponent(key)}`,
    );
  }
  plan(id: string) {
    return this.http.get<OrderTask[]>(
      `${this.base}/${encodeURIComponent(id)}/plan`,
    );
  }
  timeline(id: string) {
    return this.http.get<OrderTimeline[]>(
      `${this.base}/${encodeURIComponent(id)}/timeline`,
    );
  }
  convert(source: SourceReference, key: string) {
    return this.http.post<OrderView>(`${this.base}/from-quote`, source, {
      headers: { 'Idempotency-Key': key },
    });
  }
  command(id: string, action: string, body: Command & Record<string, unknown>) {
    return this.http.post<unknown>(
      `${this.base}/${encodeURIComponent(id)}/${action}`,
      body,
      { observe: 'response' },
    );
  }
  exceptions(page: number, status: string) {
    return this.http.get<OrderPage<OrderException>>(
      '/api/v1/order-exceptions',
      { params: { page, size: 20, status } },
    );
  }
  exceptionCommand(
    id: string,
    action: 'assign' | 'resolve',
    body: Command & Record<string, unknown>,
  ) {
    return this.http.post<unknown>(
      `/api/v1/order-exceptions/${encodeURIComponent(id)}/${action}`,
      body,
      { observe: 'response' },
    );
  }
}

export function orderError(error: unknown): string {
  const e = error as {
    status?: number;
    message?: string;
    errorCode?: string;
    error?: { message?: string };
  };
  switch (e?.status) {
    case 0:
      return 'The outcome is unknown because the connection failed. Reload and reconcile before trying another operation.';
    case 401:
      return 'Sign in with an authorized OMS account and reload.';
    case 403:
      return 'Your account lacks permission for this action. Ask your administrator for the required OMS permission.';
    case 404:
      return 'This order or operation is unavailable in your scope or account. Return to the order list.';
    case 409:
      return 'The order changed or this operation key conflicts. Reload and review the latest state before continuing.';
    case 422:
      return (
        e.error?.message ||
        'The server rejected this operation. Review quantities, evidence, holds and the latest order state.'
      );
    case 502:
    case 503:
    case 504:
      return 'The service is unavailable and the operation outcome may be unknown. Reload and reconcile before retrying.';
    default:
      return (
        e?.error?.message ||
        e?.message ||
        'Could not complete this request. Reload and review the order.'
      );
  }
}
