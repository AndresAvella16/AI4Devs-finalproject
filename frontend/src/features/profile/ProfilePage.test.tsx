import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { ProfileRequest } from '../../api/profile';
import { saveSession } from '../../api/session';
import {
  API,
  authResponse,
  CV_FILE_NAME,
  demoProfile,
  exampleCvDraft,
  noProfile,
  problem,
  savedProfileResponse,
} from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

function renderProfile() {
  saveSession(authResponse());
  return renderWithProviders({ path: '/perfil' });
}

function pdf(name = CV_FILE_NAME, size = 2048): File {
  return new File([new Uint8Array(size)], name, { type: 'application/pdf' });
}

/**
 * Cuenta las subidas del CV que llegan al "backend" y responde con `respond` (por defecto, el borrador). Comprueba que
 * llega un multipart con el campo `file`; el nombre y el contenido del fichero no: el puente de Vitest entre el `File`
 * de jsdom y el `fetch` de Node los pierde (llega como "blob" vacío). La subida real la cubre el E2E.
 */
function countUploads(respond: () => Response | Promise<Response> = () => HttpResponse.json(exampleCvDraft())) {
  const calls = { count: 0 };
  server.use(
    http.post(`${API}/profile/cv`, async ({ request }) => {
      calls.count += 1;
      const multipart = (request.headers.get('Content-Type') ?? '').startsWith('multipart/form-data');
      if (!multipart || !(await request.text()).includes('name="file"')) {
        return problem(400, 'validation-error', 'Datos de entrada no válidos', 'Falta el campo file.');
      }
      return respond();
    }),
  );
  return calls;
}

/** Guarda el cuerpo de cada `PUT /profile`. */
function capturePuts(created: boolean) {
  const bodies: ProfileRequest[] = [];
  server.use(
    http.put(`${API}/profile`, async ({ request }) => {
      const body = (await request.json()) as ProfileRequest;
      bodies.push(body);
      return savedProfileResponse(body, created);
    }),
  );
  return bodies;
}

async function uploadPdf(user: ReturnType<typeof renderProfile>['user'], file = pdf()) {
  await user.upload(await screen.findByLabelText('Selecciona tu CV en PDF'), file);
}

function form() {
  return screen.getByRole('form', { name: 'Perfil profesional' });
}

describe('ProfilePage — onboarding: subida del CV y formulario del perfil (HU-01)', () => {
  it('sin perfil ofrece subir el CV o rellenarlo a mano, sin formulario todavía', async () => {
    server.use(noProfile());
    renderProfile();

    expect(await screen.findByRole('heading', { level: 1, name: 'Crea tu perfil' })).toBeInTheDocument();
    expect(screen.getByTestId('cv-dropzone')).toHaveTextContent('Arrastra aquí tu CV en PDF');
    expect(screen.getByLabelText('Selecciona tu CV en PDF')).toHaveAttribute('type', 'file');
    expect(screen.getByRole('button', { name: 'Rellenar a mano' })).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Perfil profesional' })).not.toBeInTheDocument();
  });

  it('al subir un PDF muestra el procesamiento y después el formulario prerrellenado con el borrador', async () => {
    server.use(noProfile());
    const uploads = countUploads(async () => {
      await delay(50);
      return HttpResponse.json(exampleCvDraft());
    });
    const { user } = renderProfile();

    await uploadPdf(user);

    expect(await screen.findByTestId('cv-upload-pending')).toHaveTextContent('Analizando tu CV');
    await screen.findByRole('form', { name: 'Perfil profesional' });
    expect(uploads.count).toBe(1);
    expect(screen.queryByTestId('cv-upload-pending')).not.toBeInTheDocument();
    expect(screen.getByTestId('cv-draft-notice')).toHaveTextContent(`Datos extraídos de «${CV_FILE_NAME}»`);

    const profile = within(form());
    expect(profile.getByLabelText('Titular')).toHaveValue('Desarrolladora Web Junior · Java');
    expect(profile.getByLabelText('Resumen')).toHaveValue(exampleCvDraft().summary);
    expect(profile.getByLabelText('Años de experiencia')).toHaveValue(2);
    expect(profile.getByLabelText('Ubicación')).toHaveValue('Valencia');

    const chips = profile.getAllByTestId('skill-chip');
    expect(chips.map((chip) => (within(chip).getByRole('textbox') as HTMLInputElement).value)).toEqual([
      'Java',
      'Spring Boot',
      'PostgreSQL',
      'Git',
      'React',
    ]);
    expect(within(chips[0] as HTMLElement).getByRole('combobox')).toHaveValue('AVANZADO');

    const experience = within(profile.getByRole('group', { name: 'Experiencia 1' }));
    expect(experience.getByLabelText('Empresa')).toHaveValue('Tecnologías Turia S.L.');
    expect(experience.getByLabelText('Puesto')).toHaveValue('Desarrolladora Junior');
    expect(experience.getByLabelText('Inicio')).toHaveValue('2025-03-01');
    expect(experience.getByLabelText('Puesto actual')).toBeChecked();
    expect(experience.getByLabelText('Fin')).toBeDisabled();

    expect(within(profile.getByRole('group', { name: 'Formación 1' })).getByLabelText('Título')).toHaveValue(
      'CFGS Desarrollo de Aplicaciones Web (DAW)',
    );
    expect(profile.getAllByTestId('language-item')).toHaveLength(2);
  });

  it('se puede editar, añadir y quitar antes de guardar; la primera vez (201) lleva al dashboard', async () => {
    server.use(noProfile());
    const puts = capturePuts(true);
    const { user, router } = renderProfile();
    await uploadPdf(user);
    await screen.findByRole('form', { name: 'Perfil profesional' });
    const profile = within(form());

    const headline = profile.getByLabelText('Titular');
    await user.clear(headline);
    await user.type(headline, 'Desarrolladora Java Junior');
    await user.click(profile.getByRole('button', { name: 'Quitar React' }));
    await user.click(profile.getByRole('button', { name: 'Añadir habilidad' }));
    const newSkill = profile.getByLabelText('Habilidad 5');
    expect(newSkill).toHaveFocus();
    await user.type(newSkill, 'Docker');
    await user.selectOptions(profile.getByLabelText('Nivel de la habilidad 5'), 'BASICO');

    await user.click(profile.getByRole('button', { name: 'Añadir experiencia' }));
    const second = within(profile.getByRole('group', { name: 'Experiencia 2' }));
    await user.type(second.getByLabelText('Empresa'), 'Prácticas Ficticias S.L.');
    await user.type(second.getByLabelText('Puesto'), 'Becaria');
    fireEvent.change(second.getByLabelText('Inicio'), { target: { value: '2024-03-01' } });
    fireEvent.change(second.getByLabelText('Fin'), { target: { value: '2025-02-28' } });

    await user.click(profile.getByRole('button', { name: 'Quitar idioma 1' }));
    await user.click(profile.getByRole('button', { name: 'Quitar formación 1' }));

    await user.click(profile.getByRole('button', { name: 'Guardar perfil' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(await screen.findByText('Perfil creado')).toBeInTheDocument();
    expect(puts).toEqual([
      {
        headline: 'Desarrolladora Java Junior',
        summary: exampleCvDraft().summary,
        yearsExperience: 2,
        location: 'Valencia',
        skills: [
          { name: 'Java', level: 'AVANZADO', years: 2 },
          { name: 'Spring Boot', level: 'INTERMEDIO', years: 2 },
          { name: 'PostgreSQL', level: 'INTERMEDIO', years: 1 },
          { name: 'Git', level: 'INTERMEDIO', years: 2 },
          { name: 'Docker', level: 'BASICO', years: null },
        ],
        experiences: [
          exampleCvDraft().experiences[0],
          { company: 'Prácticas Ficticias S.L.', role: 'Becaria', startDate: '2024-03-01', endDate: '2025-02-28', description: null },
        ],
        education: [],
        languages: [{ language: 'Inglés', level: 'B2' }],
      },
    ]);
  });

  it('un fichero que no es PDF o pesa más de 5 MB se rechaza sin enviar nada', async () => {
    server.use(noProfile());
    const uploads = countUploads();
    const { user } = renderProfile();

    const dropzone = await screen.findByTestId('cv-dropzone');
    fireEvent.drop(dropzone, { dataTransfer: { files: [new File(['x'], 'foto.png', { type: 'image/png' })] } });
    expect(await screen.findByTestId('cv-file-error')).toHaveTextContent('Solo se admiten PDF de hasta 5 MB.');

    await uploadPdf(user, pdf('grande.pdf', 5 * 1024 * 1024 + 1));
    expect(screen.getByTestId('cv-file-error')).toHaveTextContent('Solo se admiten PDF de hasta 5 MB.');

    expect(uploads.count).toBe(0);
    expect(screen.queryByTestId('cv-upload-pending')).not.toBeInTheDocument();
  });

  it('un PDF soltado en la zona de arrastre se sube', async () => {
    server.use(noProfile());
    const uploads = countUploads();
    renderProfile();

    fireEvent.drop(await screen.findByTestId('cv-dropzone'), { dataTransfer: { files: [pdf()] } });

    await screen.findByRole('form', { name: 'Perfil profesional' });
    expect(uploads.count).toBe(1);
  });

  it.each([
    [
      '400 en file (más de 10 páginas)',
      () =>
        problem(400, 'validation-error', 'Datos de entrada no válidos', 'Revisa los campos marcados.', {
          errors: [{ field: 'file', message: 'El PDF no puede tener más de 10 páginas.' }],
        }),
      'El PDF no puede tener más de 10 páginas.',
    ],
    [
      '415 invalid-cv-file',
      () => problem(415, 'invalid-cv-file', 'Fichero no válido', 'Solo se admiten PDF de hasta 5 MB.'),
      'Solo se admiten PDF de hasta 5 MB.',
    ],
  ])('muestra el mensaje del backend si rechaza el fichero (%s)', async (_case, respond, message) => {
    server.use(noProfile());
    countUploads(respond);
    const { user } = renderProfile();

    await uploadPdf(user);

    expect(await screen.findByTestId('cv-file-error')).toHaveTextContent(message);
    expect(screen.queryByRole('form', { name: 'Perfil profesional' })).not.toBeInTheDocument();
  });

  it('un PDF sin texto (422) sugiere subir otro o rellenar el perfil a mano', async () => {
    server.use(noProfile());
    countUploads(() =>
      problem(422, 'cv-without-text', 'El PDF no tiene texto', 'No hemos podido leer texto en el PDF.'),
    );
    const { user } = renderProfile();

    await uploadPdf(user);

    const notice = await screen.findByTestId('cv-without-text');
    expect(notice).toHaveTextContent('Sube un PDF con texto (no escaneado) o rellena tu perfil a mano');
    await user.click(within(notice).getByRole('button', { name: 'Rellenar a mano' }));

    expect(within(form()).getByLabelText('Titular')).toHaveValue('');
    expect(within(form()).queryAllByTestId('skill-chip')).toHaveLength(0);
  });

  it.each([
    ['502', 502, 'llm-invalid-response', 'Respuesta de la IA no válida'],
    ['503 con Retry-After', 503, 'llm-overloaded', 'La IA está saturada'],
    ['504', 504, 'llm-timeout', 'La IA no ha respondido a tiempo'],
    ['429 con Retry-After', 429, 'rate-limit-exceeded', 'Demasiadas peticiones'],
  ])('si la IA no está disponible (%s) permite reintentar y conserva rellenar a mano', async (_case, status, slug, title) => {
    server.use(noProfile());
    let fail = true;
    const uploads = countUploads(() => {
      if (!fail) return HttpResponse.json(exampleCvDraft());
      return HttpResponse.json(
        { type: `https://jobpilot.dev/problems/${slug}`, title, status, detail: 'Inténtalo de nuevo en unos segundos.' },
        { status, headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '30' } },
      );
    });
    const { user } = renderProfile();

    await uploadPdf(user);

    const error = await screen.findByTestId('cv-upload-error');
    expect(error).toHaveTextContent(title);
    expect(error).toHaveTextContent('Inténtalo de nuevo en unos segundos.');
    if (slug === 'llm-overloaded' || slug === 'rate-limit-exceeded') {
      expect(error).toHaveTextContent('Vuelve a intentarlo dentro de 30 segundos.');
    }
    expect(screen.getByRole('button', { name: 'Rellenar a mano' })).toBeInTheDocument();

    fail = false;
    await user.click(within(error).getByRole('button', { name: 'Reintentar' }));

    await screen.findByRole('form', { name: 'Perfil profesional' });
    expect(uploads.count).toBe(2);
    expect(screen.queryByTestId('cv-upload-error')).not.toBeInTheDocument();
  });

  it('el perfil hecho a mano se guarda sin subir CV', async () => {
    server.use(noProfile());
    const puts = capturePuts(true);
    const { user, router } = renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Rellenar a mano' }));
    await user.type(within(form()).getByLabelText('Titular'), 'Analista de datos');
    await user.click(within(form()).getByRole('button', { name: 'Guardar perfil' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(puts.map((body) => body.headline)).toEqual(['Analista de datos']);
  });

  it('con perfil muestra el guardado; guardar (200) se queda en la página con un aviso', async () => {
    const puts = capturePuts(false);
    const { user, router } = renderProfile();

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu perfil' })).toBeInTheDocument();
    const profile = within(form());
    expect(profile.getByLabelText('Titular')).toHaveValue(demoProfile().headline);
    const experience = within(profile.getByRole('group', { name: 'Experiencia 1' }));
    expect(experience.getByLabelText('Fin')).toHaveValue('2025-12-31');
    expect(experience.getByLabelText('Puesto actual')).not.toBeChecked();
    expect(screen.getByTestId('profile-cv-file')).toHaveTextContent('cv-demo.pdf');

    await user.click(profile.getByRole('button', { name: 'Guardar perfil' }));

    expect(await screen.findByText('Perfil guardado')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/perfil');
    const saved = demoProfile();
    expect(puts).toEqual([
      {
        headline: saved.headline,
        summary: saved.summary,
        yearsExperience: saved.yearsExperience,
        location: saved.location,
        skills: saved.skills,
        experiences: saved.experiences,
        education: saved.education,
        languages: saved.languages,
      },
    ]);
  });

  it('volver a subir un CV con perfil pide confirmación antes de sobrescribir el formulario', async () => {
    const uploads = countUploads();
    const { user } = renderProfile();
    await screen.findByRole('heading', { level: 1, name: 'Tu perfil' });

    await uploadPdf(user, pdf('cv-nuevo.pdf'));

    const confirm = screen.getByTestId('cv-replace-confirm');
    expect(confirm).toHaveTextContent('«cv-nuevo.pdf»');
    await user.click(within(confirm).getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByTestId('cv-replace-confirm')).not.toBeInTheDocument();
    expect(uploads.count).toBe(0);
    expect(within(form()).getByLabelText('Titular')).toHaveValue(demoProfile().headline);

    await uploadPdf(user, pdf('cv-nuevo.pdf'));
    await user.click(within(screen.getByTestId('cv-replace-confirm')).getByRole('button', { name: 'Sí, reemplazar' }));

    await waitFor(() => expect(within(form()).getByLabelText('Titular')).toHaveValue(exampleCvDraft().headline));
    expect(uploads.count).toBe(1);
    expect(within(form()).getAllByTestId('skill-chip')).toHaveLength(5);
  });

  it('la validación del cliente marca la habilidad repetida y no envía el perfil', async () => {
    const puts = capturePuts(false);
    const { user } = renderProfile();
    await screen.findByRole('heading', { level: 1, name: 'Tu perfil' });
    const profile = within(form());

    await user.click(profile.getByRole('button', { name: 'Añadir habilidad' }));
    await user.type(profile.getByLabelText('Habilidad 3'), 'JAVA');
    await user.click(profile.getByRole('button', { name: 'Guardar perfil' }));

    const skill = profile.getByLabelText('Habilidad 3');
    await waitFor(() => expect(skill).toHaveAttribute('aria-invalid', 'true'));
    expect(profile.getByText('La habilidad está repetida (no se distinguen mayúsculas)')).toBeInTheDocument();
    expect(puts).toEqual([]);
  });

  it('los errores del backend (400) se llevan a su campo', async () => {
    server.use(
      http.put(`${API}/profile`, () =>
        problem(400, 'validation-error', 'Datos de entrada no válidos', 'Revisa los campos marcados.', {
          errors: [{ field: 'experiences[0].endDate', message: 'La fecha de fin no puede ser anterior a la de inicio.' }],
        }),
      ),
    );
    const { user } = renderProfile();
    await screen.findByRole('heading', { level: 1, name: 'Tu perfil' });

    await user.click(within(form()).getByRole('button', { name: 'Guardar perfil' }));

    const end = within(within(form()).getByRole('group', { name: 'Experiencia 1' })).getByLabelText('Fin');
    await waitFor(() => expect(end).toHaveAttribute('aria-invalid', 'true'));
    expect(within(form()).getByText('La fecha de fin no puede ser anterior a la de inicio.')).toBeInTheDocument();
  });

  it('un 409 profile-concurrent-update muestra un aviso general', async () => {
    server.use(
      http.put(`${API}/profile`, () =>
        problem(
          409,
          'profile-concurrent-update',
          'El perfil se ha guardado a la vez desde otra petición',
          'Recarga la página y vuelve a intentarlo.',
        ),
      ),
    );
    const { user } = renderProfile();
    await screen.findByRole('heading', { level: 1, name: 'Tu perfil' });

    await user.click(within(form()).getByRole('button', { name: 'Guardar perfil' }));

    const error = await screen.findByTestId('profile-save-error');
    expect(error).toHaveTextContent('El perfil se ha guardado a la vez desde otra petición');
    expect(error).toHaveTextContent('Recarga la página y vuelve a intentarlo.');
  });

  it('si no se puede cargar el perfil avisa y permite reintentar', async () => {
    let fail = true;
    server.use(
      http.get(`${API}/profile`, () =>
        fail ? problem(400, 'malformed-request', 'Petición mal formada', 'Error de prueba.') : HttpResponse.json(demoProfile()),
      ),
    );
    const { user } = renderProfile();

    const error = await screen.findByTestId('profile-load-error');
    fail = false;
    await user.click(within(error).getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu perfil' })).toBeInTheDocument();
  });
});
