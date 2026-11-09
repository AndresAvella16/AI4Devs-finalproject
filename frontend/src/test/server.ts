import { setupServer } from 'msw/node';
import { handlers, offerHandlers } from './handlers';

export const server = setupServer(...handlers, ...offerHandlers);
