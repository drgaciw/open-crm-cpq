import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding,
  withExperimentalAutoCleanupInjectors,
} from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { appRoutes } from './app.routes';
import {
  apiResponseInterceptor,
  authRedirectInterceptor,
  errorInterceptor,
  loadingInterceptor,
} from '@frontend/shared-data-access';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      appRoutes,
      withComponentInputBinding(),
      withExperimentalAutoCleanupInjectors(),
    ),
    provideHttpClient(
      withXhr(),
      // authRedirectInterceptor: a 401 with the gateway's login hint sends the browser to the BFF login (ADR-016).
      withInterceptors([loadingInterceptor, apiResponseInterceptor, authRedirectInterceptor, errorInterceptor])
    ),
    provideAnimationsAsync(),
  ],
};
