import { setupServer } from 'msw/node';
import { applicationHandlers, handlers, offerHandlers } from './handlers';

export const server = setupServer(...handlers, ...offerHandlers, ...applicationHandlers);
