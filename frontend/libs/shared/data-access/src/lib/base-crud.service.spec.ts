import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Injectable } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
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
class TestCrudService extends BaseCrudService<
  TestEntity,
  CreateTestEntity,
  Partial<TestEntity>
> {
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
        provideHttpClientTesting(),
      ],
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
    it('should fetch paginated list of items', (done) => {
      const mockResponse: SpringPageResponse<TestEntity> = {
        content: [
          { id: '1', name: 'Item 1' },
          { id: '2', name: 'Item 2' },
        ],
        totalElements: 10,
        totalPages: 5,
        size: 2,
        number: 0,
      };

      service.list({ page: 0, size: 2 }).subscribe((items) => {
        expect(items).toEqual(mockResponse.content);
        expect(service.items()).toEqual(mockResponse.content);
        expect(service.pagination().totalElements).toBe(10);
        expect(service.pagination().page).toBe(0);
        expect(service.loading()).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/v1/test-entities?page=0&size=2');
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should handle sort parameter', (done) => {
      const mockResponse: SpringPageResponse<TestEntity> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0,
      };

      service.list({ sort: 'name,asc' }).subscribe(() => {
        done();
      });

      const req = httpMock.expectOne('/api/v1/test-entities?sort=name%2Casc');
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('getById', () => {
    it('should fetch a single item by ID', (done) => {
      const mockItem: TestEntity = { id: '1', name: 'Item 1' };

      service.getById('1').subscribe((item) => {
        expect(item).toEqual(mockItem);
        expect(service.selectedItem()).toEqual(mockItem);
        expect(service.loading()).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('GET');
      req.flush(mockItem);
    });
  });

  describe('create', () => {
    it('should create a new item', (done) => {
      const createRequest: CreateTestEntity = { name: 'New Item' };
      const createdItem: TestEntity = { id: '3', name: 'New Item' };

      service.create(createRequest).subscribe((item) => {
        expect(item).toEqual(createdItem);
        expect(service.selectedItem()).toEqual(createdItem);
        expect(service.items()[0]).toEqual(createdItem);
        expect(service.loading()).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/v1/test-entities');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(createdItem);
    });
  });

  describe('update', () => {
    it('should update an existing item', (done) => {
      // First, populate the items list
      service['itemsSignal'].set([
        { id: '1', name: 'Item 1' },
        { id: '2', name: 'Item 2' },
      ]);

      const updateRequest: Partial<TestEntity> = { name: 'Updated Item' };
      const updatedItem: TestEntity = { id: '1', name: 'Updated Item' };

      service.update('1', updateRequest).subscribe((item) => {
        expect(item).toEqual(updatedItem);
        expect(service.selectedItem()).toEqual(updatedItem);
        expect(service.items()[0]).toEqual(updatedItem);
        expect(service.loading()).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateRequest);
      req.flush(updatedItem);
    });
  });

  describe('delete', () => {
    it('should delete an item', (done) => {
      // First, populate the items list
      service['itemsSignal'].set([
        { id: '1', name: 'Item 1' },
        { id: '2', name: 'Item 2' },
      ]);

      service.delete('1').subscribe(() => {
        expect(service.items().length).toBe(1);
        expect(service.items()[0].id).toBe('2');
        expect(service.loading()).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should clear selection if deleted item was selected', (done) => {
      service['itemsSignal'].set([{ id: '1', name: 'Item 1' }]);
      service['selectedItemSignal'].set({ id: '1', name: 'Item 1' });

      service.delete('1').subscribe(() => {
        expect(service.selectedItem()).toBeNull();
        done();
      });

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      req.flush(null);
    });
  });

  describe('search', () => {
    it('should search for items', (done) => {
      const mockResponse: SpringPageResponse<TestEntity> = {
        content: [{ id: '1', name: 'Search Result' }],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0,
      };

      service.search('query', { page: 0, size: 20 }).subscribe((items) => {
        expect(items).toEqual(mockResponse.content);
        expect(service.items()).toEqual(mockResponse.content);
        done();
      });

      const req = httpMock.expectOne(
        '/api/v1/test-entities/search?q=query&page=0&size=20',
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
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
        totalPages: 5,
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
        totalPages: 5,
      });

      expect(service.hasNextPage()).toBe(true);

      service['paginationSignal'].set({
        page: 4,
        size: 20,
        totalElements: 100,
        totalPages: 5,
      });

      expect(service.hasNextPage()).toBe(false);
    });

    it('should compute hasPreviousPage correctly', () => {
      service['paginationSignal'].set({
        page: 0,
        size: 20,
        totalElements: 100,
        totalPages: 5,
      });

      expect(service.hasPreviousPage()).toBe(false);

      service['paginationSignal'].set({
        page: 2,
        size: 20,
        totalElements: 100,
        totalPages: 5,
      });

      expect(service.hasPreviousPage()).toBe(true);
    });
  });
});
