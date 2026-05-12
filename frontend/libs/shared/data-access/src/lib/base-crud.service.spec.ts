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

  const pageResponse = (content: TestEntity[], overrides: Partial<SpringPageResponse<TestEntity>> = {}): SpringPageResponse<TestEntity> => ({
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    size: 20,
    number: 0,
    ...overrides,
  });

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
      const mockResponse = pageResponse(
        [
          { id: '1', name: 'Item 1' },
          { id: '2', name: 'Item 2' }
        ],
        { totalElements: 10, totalPages: 5, size: 2, number: 0 }
      );

      const listPromise = firstValueFrom(service.list({ page: 0, size: 2 }));
      expect(service.loading()).toBe(true);

      const req = httpMock.expectOne('/api/v1/test-entities?page=0&size=2');
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);

      await expect(listPromise).resolves.toEqual(mockResponse.content);
      expect(service.items()).toEqual(mockResponse.content);
      expect(service.pagination().totalElements).toBe(10);
      expect(service.pagination().page).toBe(0);
      expect(service.loading()).toBe(false);
    });

    it('should pass through sort parameter values', async () => {
      const listPromise = firstValueFrom(service.list({ sort: 'name,asc' }));

      const req = httpMock.expectOne((request) => request.url === '/api/v1/test-entities');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('sort')).toBe('name,asc');
      req.flush(pageResponse([]));

      await expect(listPromise).resolves.toEqual([]);
    });
  });

  describe('getById', () => {
    it('should fetch a single item by ID', async () => {
      const mockItem: TestEntity = { id: '1', name: 'Item 1' };

      const getPromise = firstValueFrom(service.getById('1'));
      expect(service.loading()).toBe(true);

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('GET');
      req.flush(mockItem);

      await expect(getPromise).resolves.toEqual(mockItem);
      expect(service.selectedItem()).toEqual(mockItem);
      expect(service.loading()).toBe(false);
    });
  });

  describe('create', () => {
    it('should create a new item', async () => {
      const createRequest: CreateTestEntity = { name: 'New Item' };
      const createdItem: TestEntity = { id: '3', name: 'New Item' };

      const createPromise = firstValueFrom(service.create(createRequest));
      expect(service.loading()).toBe(true);

      const req = httpMock.expectOne('/api/v1/test-entities');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(createdItem);

      await expect(createPromise).resolves.toEqual(createdItem);
      expect(service.selectedItem()).toEqual(createdItem);
      expect(service.items()[0]).toEqual(createdItem);
      expect(service.loading()).toBe(false);
    });
  });

  describe('update', () => {
    it('should update an existing item', async () => {
      const listPromise = firstValueFrom(service.list());
      httpMock.expectOne('/api/v1/test-entities').flush(pageResponse([
        { id: '1', name: 'Item 1' },
        { id: '2', name: 'Item 2' },
      ]));
      await listPromise;

      const updateRequest: Partial<TestEntity> = { name: 'Updated Item' };
      const updatedItem: TestEntity = { id: '1', name: 'Updated Item' };

      const updatePromise = firstValueFrom(service.update('1', updateRequest));

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateRequest);
      req.flush(updatedItem);

      await expect(updatePromise).resolves.toEqual(updatedItem);
      expect(service.selectedItem()).toEqual(updatedItem);
      expect(service.items()[0]).toEqual(updatedItem);
      expect(service.loading()).toBe(false);
    });
  });

  describe('delete', () => {
    it('should delete an item', async () => {
      const listPromise = firstValueFrom(service.list());
      httpMock.expectOne('/api/v1/test-entities').flush(pageResponse([
        { id: '1', name: 'Item 1' },
        { id: '2', name: 'Item 2' },
      ]));
      await listPromise;

      const deletePromise = firstValueFrom(service.delete('1'));

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      await expect(deletePromise).resolves.toBeNull();
      expect(service.items().length).toBe(1);
      expect(service.items()[0].id).toBe('2');
      expect(service.loading()).toBe(false);
    });

    it('should clear selection if deleted item was selected', async () => {
      const mockItem = { id: '1', name: 'Item 1' };
      const listPromise = firstValueFrom(service.list());
      httpMock.expectOne('/api/v1/test-entities').flush(pageResponse([mockItem]));
      await listPromise;

      const getPromise = firstValueFrom(service.getById('1'));
      httpMock.expectOne('/api/v1/test-entities/1').flush(mockItem);
      await getPromise;

      const deletePromise = firstValueFrom(service.delete('1'));

      const req = httpMock.expectOne('/api/v1/test-entities/1');
      req.flush(null);

      await deletePromise;
      expect(service.selectedItem()).toBeNull();
    });
  });

  describe('search', () => {
    it('should search for items', async () => {
      const mockResponse = pageResponse([{ id: '1', name: 'Search Result' }]);

      const searchPromise = firstValueFrom(service.search('query', { page: 0, size: 20 }));

      const req = httpMock.expectOne('/api/v1/test-entities/search?q=query&page=0&size=20');
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);

      await expect(searchPromise).resolves.toEqual(mockResponse.content);
      expect(service.items()).toEqual(mockResponse.content);
    });
  });

  describe('state management', () => {
    it('should clear selection', async () => {
      const mockItem = { id: '1', name: 'Item 1' };
      const getPromise = firstValueFrom(service.getById('1'));
      httpMock.expectOne('/api/v1/test-entities/1').flush(mockItem);
      await getPromise;
      expect(service.selectedItem()).not.toBeNull();

      service.clearSelection();
      expect(service.selectedItem()).toBeNull();
    });

    it('should clear items', async () => {
      const listPromise = firstValueFrom(service.list());
      httpMock.expectOne('/api/v1/test-entities').flush(pageResponse([{ id: '1', name: 'Item 1' }]));
      await listPromise;
      expect(service.items().length).toBe(1);

      service.clearItems();
      expect(service.items().length).toBe(0);
    });

    it('should reset all state', async () => {
      const listPromise = firstValueFrom(service.list({ page: 2, size: 10 }));
      httpMock.expectOne('/api/v1/test-entities?page=2&size=10').flush(pageResponse(
        [{ id: '1', name: 'Item 1' }],
        { number: 2, size: 10, totalElements: 50, totalPages: 5 }
      ));
      await listPromise;

      const getPromise = firstValueFrom(service.getById('1'));
      httpMock.expectOne('/api/v1/test-entities/1').flush({ id: '1', name: 'Item 1' });
      await getPromise;

      service.reset();

      expect(service.items().length).toBe(0);
      expect(service.selectedItem()).toBeNull();
      expect(service.loading()).toBe(false);
      expect(service.pagination().page).toBe(0);
      expect(service.pagination().totalElements).toBe(0);
    });
  });

  describe('pagination computed signals', () => {
    it('should compute hasNextPage correctly', async () => {
      const firstPagePromise = firstValueFrom(service.list());
      httpMock.expectOne('/api/v1/test-entities').flush(pageResponse([], {
        number: 0,
        totalElements: 100,
        totalPages: 5,
      }));
      await firstPagePromise;

      expect(service.hasNextPage()).toBe(true);

      const lastPagePromise = firstValueFrom(service.list({ page: 4 }));
      httpMock.expectOne('/api/v1/test-entities?page=4').flush(pageResponse([], {
        number: 4,
        totalElements: 100,
        totalPages: 5,
      }));
      await lastPagePromise;

      expect(service.hasNextPage()).toBe(false);
    });

    it('should compute hasPreviousPage correctly', async () => {
      const firstPagePromise = firstValueFrom(service.list());
      httpMock.expectOne('/api/v1/test-entities').flush(pageResponse([], {
        number: 0,
        totalElements: 100,
        totalPages: 5,
      }));
      await firstPagePromise;

      expect(service.hasPreviousPage()).toBe(false);

      const middlePagePromise = firstValueFrom(service.list({ page: 2 }));
      httpMock.expectOne('/api/v1/test-entities?page=2').flush(pageResponse([], {
        number: 2,
        totalElements: 100,
        totalPages: 5,
      }));
      await middlePagePromise;

      expect(service.hasPreviousPage()).toBe(true);
    });
  });
});
