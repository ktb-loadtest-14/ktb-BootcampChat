import { afterEach, describe, expect, it, vi } from 'vitest';
import fileService from '../fileService';

vi.mock('../../components/Toast', () => ({
  Toast: {
    error: vi.fn(),
  },
}));

describe('fileService', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
  });

  it('handles upload size limit errors without logging console errors', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    const result = fileService.handleUploadError(
      Object.assign(new Error('파일 크기는 5MB를 초과할 수 없습니다.'), {
        status: 413,
      })
    );

    expect(result).toEqual({
      success: false,
      message: '파일 크기는 5MB를 초과할 수 없습니다.',
    });
    expect(consoleError).not.toHaveBeenCalled();
  });

  it('uses the CloudFront URL returned by the backend without adding auth query params', () => {
    const file = {
      filename: 'avatar.png',
      url: 'https://d16225pinz5a60.cloudfront.net/profiles/avatar.png',
    };

    expect(fileService.getPreviewUrl(file, 'token', 'session-id', true)).toBe(file.url);
    expect(fileService.getFileUrl(file, true)).toBe(file.url);
  });

  it('keeps the authenticated backend preview route for legacy responses without a URL', () => {
    vi.stubEnv('NEXT_PUBLIC_API_URL', 'http://localhost:5001');

    const url = fileService.getPreviewUrl(
      { filename: 'legacy.png' },
      'token',
      'session-id',
      true
    );

    expect(url).toContain('/api/files/view/legacy.png');
    expect(url).toContain('token=token');
    expect(url).toContain('sessionId=session-id');
  });

  it('resolves a server-provided relative URL against the API and keeps authentication', () => {
    vi.stubEnv('NEXT_PUBLIC_API_URL', 'http://localhost:5001');

    const url = fileService.getPreviewUrl(
      {
        filename: 'stored.png',
        url: '/api/files/view/stored.png',
      },
      'token',
      'session-id',
      true
    );

    expect(url).toContain('http://localhost:5001/api/files/view/stored.png');
    expect(url).toContain('token=token');
    expect(url).toContain('sessionId=session-id');
  });
});
