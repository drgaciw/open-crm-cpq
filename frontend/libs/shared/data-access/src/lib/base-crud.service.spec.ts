import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Injectable } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { BaseCrudService } from './base-crud.service';
import { SpringPageResponse } from '@frontend/shared-models';

interface TestEntity {
  id: string;
  name: string;
}

interface CreateTestEntity {
  name: string;
}

@Injectable()
class TestCrudService extends BaseCrudService<TestEntity, CreateTestEntity, Partial<TestEntity>> {
  protected override basePath = '/api/v1/test-entities';
}

describe('BaseCrudService', () => {
  let service: TestCrudService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TestCrudService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(TestCrudService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('list', () => {
    it('should fetch paginated list of items', async () => {
      const mockResponse: SpringPageResponse<TestEntity> = {
        content: [
          { id: '1', name: 'Item 1' },
          { id: '2', name: 'Item 2' }
        ],
        totalElements: 10,
        totalPages: 5,
        size: 2,
        number: 0
      };

      const responsePromise = firstValueFrom(service.list({ page: 0, size: 2 }));

      const req = httpMock.expectOne('/api/v1/test-entities?page=0&size=2');
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);

      await expect(responsePromise).resolves.toEqual(mockResponse.content);
      expect(service.items()).toEqual(mockResponse.content);
      expect(service.pagination().totalElements).toBe(10);
      expect(service.pagination().page).toBe(0);
      expect(service.loading()).toBe(false);
    });

    it('should handle sort parameter', async () => {
      const mockResponse: SpringPageResponse<TestEntity> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0
      };

      const responsePromise = firstValueFrom(service.list({ sort: 'name,asc' }));

      const req = httpMock.expectOne((request) =>
        request.url === '/api/v1/test-entities' &&
        request.params.get('sort') === 'name,asc'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);

      await expect(responsePromise).resolves.toEqual(mockResponse.content);
    });
  });

  describe('getById', () => {
    it('should fetch a single item by ID', async () => {
      const mockItem: TestEntity = { id: '1', name: 'Item 1' };

      const responsePromise = firstValueFrom(service.getById('1'));

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('GET');
      req.flush(mockItem);

      await expect(responsePromise).resolves.toEqual(mockItem);
      expect(service.selectedItem()).toEqual(mockItem);
      expect(service.loading()).toBe(false);
    });
  });

  describe('create', () => {
    it('should create a new item', async () => {
      const createRequest: CreateTestEntity = { name: 'New Item' };
      const createdItem: TestEntity = { id: '3', name: 'New Item' };

      const responsePromise = firstValueFrom(service.create(createRequest));

      const req = httpMock.expectOne('/api/v1/test-entities');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(createdItem);

      await expect(responsePromise).resolves.toEqual(createdItem);
      expect(service.selectedItem()).toEqual(createdItem);
      expect(service.items()[0]).toEqual(createdItem);
      expect(service.loading()).toBe(false);
    });
  });

  describe('update', () => {
    it('should update an existing item', async () => {
      // First, populate the items list
      service['itemsSignal'].set([
        { id: '1', name: 'Item 1' },
        { id: '2', name: 'Item 2' }
      ]);

      const updateRequest: Partial<TestEntity> = { name: 'Updated Item' };
      const updatedItem: TestEntity = { id: '1', name: 'Updated Item' };

      const responsePromise = firstValueFrom(service.update('1', updateRequest));

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateRequest);
      req.flush(updatedItem);

      await expect(responsePromise).resolves.toEqual(updatedItem);
      expect(service.selectedItem()).toEqual(updatedItem);
      expect(service.items()[0]).toEqual(updatedItem);
      expect(service.loading()).toBe(false);
    });
  });

  describe('delete', () => {
    it('should delete an item', async () => {
      // First, populate the items list
      service['itemsSignal'].set([
        { id: '1', name: 'Item 1' },
        { id: '2', name: 'Item 2' }
      ]);

      const responsePromise = firstValueFrom(service.delete('1'));

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      await responsePromise;
      expect(service.items().length).toBe(1);
      expect(service.items()[0].id).toBe('2');
      expect(service.loading()).toBe(false);
    });

    it('should clear selection if deleted item was selected', async () => {
      service['itemsSignal'].set([{ id: '1', name: 'Item 1' }]);
      service['selectedItemSignal'].set({ id: '1', name: 'Item 1' });

      const responsePromise = firstValueFrom(service.delete('1'));

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      req.flush(null);

      await responsePromise;
      expect(service.selectedItem()).toBeNull();
    });
  });

  describe('search', () => {
    it('should search for items', async () => {
      const mockResponse: SpringPageResponse<TestEntity> = {
        content: [{ id: '1', name: 'Search Result' }],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0
      };

      const responsePromise = firstValueFrom(service.search('query', { page: 0, size: 20 }));

      const req = httpMock.expectOne((request) =>
        request.url === '/api/v1/test-entities/search' &&
        request.params.get('q') === 'query' &&
        request.params.get('page') === '0' &&
        request.params.get('size') === '20'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);

      await expect(responsePromise).resolves.toEqual(mockResponse.content);
      expect(service.items()).toEqual(mockResponse.content);
    });
  });

  describe('state management', () => {
    it('should clear selection', () => {
      service['selectedItemSignal'].set({ id: '1', name: 'Item 1' });
      expect(service.selectedItem()).not.toBeNull();

      service.clearSelection();
      expect(service.selectedItem()).toBeNull();
    });

    it('should clear items', () => {
      service['itemsSignal'].set([{ id: '1', name: 'Item 1' }]);
      expect(service.items().length).toBe(1);

      service.clearItems();
      expect(service.items().length).toBe(0);
    });

    it('should reset all state', () => {
      service['itemsSignal'].set([{ id: '1', name: 'Item 1' }]);
      service['selectedItemSignal'].set({ id: '1', name: 'Item 1' });
      service['loadingSignal'].set(true);
      service['paginationSignal'].set({
        page: 2,
        size: 10,
        totalElements: 50,
        totalPages: 5
      });

      service.reset();

      expect(service.items().length).toBe(0);
      expect(service.selectedItem()).toBeNull();
      expect(service.loading()).toBe(false);
      expect(service.pagination().page).toBe(0);
      expect(service.pagination().totalElements).toBe(0);
    });
  });

  describe('pagination computed signals', () => {
    it('should compute hasNextPage correctly', () => {
      service['paginationSignal'].set({
        page: 0,
        size: 20,
        totalElements: 100,
        totalPages: 5
      });

      expect(service.hasNextPage()).toBe(true);

      service['paginationSignal'].set({
        page: 4,
        size: 20,
        totalElements: 100,
        totalPages: 5
      });

      expect(service.hasNextPage()).toBe(false);
    });

    it('should compute hasPreviousPage correctly', () => {
      service['paginationSignal'].set({
        page: 0,
        size: 20,
        totalElements: 100,
        totalPages: 5
      });

      expect(service.hasPreviousPage()).toBe(false);

      service['paginationSignal'].set({
        page: 2,
        size: 20,
        totalElements: 100,
        totalPages: 5
      });

      expect(service.hasPreviousPage()).toBe(true);
    });
  });
});
