import { setupServer } from 'msw/node';
import { applicationHandlers, documentHandlers, handlers, offerHandlers, profileHandlers } from './handlers';

export const server = setupServer(
  ...handlers,
  ...offerHandlers,
  ...applicationHandlers,
  ...profileHandlers,
  ...documentHandlers,
);
