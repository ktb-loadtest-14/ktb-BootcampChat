import { afterEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import axiosInstance from '../axios';
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

  it('uploads chat files directly to S3 and completes pending metadata', async () => {
    const file = new File(['image-data'], 'avatar.png', { type: 'image/png' });
    const pendingFile = {
      _id: 'file-1',
      filename: 'generated.png',
      originalname: 'avatar.png',
      mimetype: 'image/png',
      size: file.size,
      url: 'https://cdn.example.test/chat/generated.png',
    };
    vi.spyOn(axiosInstance, 'post')
      .mockResolvedValueOnce({
        data: {
          directUpload: true,
          uploadUrl: 'https://bucket.s3.amazonaws.com/chat/generated.png?sig=test',
          requiredHeaders: { 'Content-Type': 'image/png' },
          file: pendingFile,
        },
      })
      .mockResolvedValueOnce({ data: { success: true, file: pendingFile } });
    const put = vi.spyOn(axios, 'put').mockResolvedValue({ status: 200 });

    const result = await fileService.uploadFile(file, vi.fn());

    expect(put).toHaveBeenCalledWith(
      'https://bucket.s3.amazonaws.com/chat/generated.png?sig=test',
      file,
      expect.objectContaining({ headers: { 'Content-Type': 'image/png' } })
    );
    expect(axiosInstance.post).toHaveBeenNthCalledWith(
      2,
      '/api/files/uploads/file-1/complete',
      {},
      expect.any(Object)
    );
    expect(result.success).toBe(true);
    expect(result.data.file.url).toBe('https://cdn.example.test/chat/generated.png');
  });

  it('keeps multipart upload as a local-storage fallback', async () => {
    const file = new File(['image-data'], 'avatar.png', { type: 'image/png' });
    vi.spyOn(axiosInstance, 'post')
      .mockResolvedValueOnce({ data: { directUpload: false } })
      .mockResolvedValueOnce({
        data: {
          success: true,
          file: { _id: 'file-1', filename: 'generated.png' },
        },
      });
    const put = vi.spyOn(axios, 'put');

    const result = await fileService.uploadFile(file, vi.fn());

    expect(put).not.toHaveBeenCalled();
    expect(axiosInstance.post).toHaveBeenNthCalledWith(
      2,
      '/api/files/upload',
      expect.any(FormData),
      expect.any(Object)
    );
    expect(result.success).toBe(true);
  });

  it('uses the same direct S3 flow for profile images', async () => {
    const file = new File(['image-data'], 'avatar.png', { type: 'image/png' });
    vi.spyOn(axiosInstance, 'post')
      .mockResolvedValueOnce({
        data: {
          directUpload: true,
          uploadUrl: 'https://bucket.s3.amazonaws.com/profiles/generated.png?sig=test',
          requiredHeaders: { 'Content-Type': 'image/png' },
          objectKey: 'profiles/user-1_generated.png',
        },
      })
      .mockResolvedValueOnce({
        data: {
          success: true,
          imageUrl: 'https://cdn.example.test/profiles/user-1_generated.png',
        },
      });
    vi.spyOn(axios, 'put').mockResolvedValue({ status: 200 });

    const result = await fileService.uploadProfileImage(file);

    expect(axios.put).toHaveBeenCalled();
    expect(axiosInstance.post).toHaveBeenNthCalledWith(
      2,
      '/api/users/profile-image/complete',
      { objectKey: 'profiles/user-1_generated.png' }
    );
    expect(result.imageUrl).toContain('cdn.example.test');
  });
});
