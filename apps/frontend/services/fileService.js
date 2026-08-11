import axios, { isCancel, CancelToken } from 'axios';
import axiosInstance from './axios';
import { Toast } from '../components/Toast';

class FileService {
  constructor() {
    this.uploadLimit = 5 * 1024 * 1024; // 백엔드 정책과 동일한 5MB
    this.activeUploads = new Map();

    this.allowedTypes = {
      image: {
        extensions: ['.jpg', '.jpeg', '.png', '.gif', '.webp'],
        mimeTypes: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
        maxSize: 5 * 1024 * 1024,
        name: '이미지'
      },
      document: {
        extensions: ['.pdf'],
        mimeTypes: ['application/pdf'],
        maxSize: 5 * 1024 * 1024,
        name: 'PDF 문서'
      }
    };
  }

  async validateFile(file) {
    if (!file) {
      const message = '파일이 선택되지 않았습니다.';
      Toast.error(message);
      return { success: false, message };
    }

    if (file.size > this.uploadLimit) {
      const message = `파일 크기는 ${this.formatFileSize(this.uploadLimit)}를 초과할 수 없습니다.`;
      Toast.error(message);
      return { success: false, message };
    }

    let isAllowedType = false;
    let maxTypeSize = 0;
    let typeConfig = null;

    for (const config of Object.values(this.allowedTypes)) {
      if (config.mimeTypes.includes(file.type)) {
        isAllowedType = true;
        maxTypeSize = config.maxSize;
        typeConfig = config;
        break;
      }
    }

    if (!isAllowedType) {
      const message = '지원하지 않는 파일 형식입니다.';
      Toast.error(message);
      return { success: false, message };
    }

    if (file.size > maxTypeSize) {
      const message = `${typeConfig.name} 파일은 ${this.formatFileSize(maxTypeSize)}를 초과할 수 없습니다.`;
      Toast.error(message);
      return { success: false, message };
    }

    const ext = this.getFileExtension(file.name);
    if (!typeConfig.extensions.includes(ext.toLowerCase())) {
      const message = '파일 확장자가 올바르지 않습니다.';
      Toast.error(message);
      return { success: false, message };
    }

    return { success: true };
  }

  async uploadFile(file, onProgress) {
    const validationResult = await this.validateFile(file);
    if (!validationResult.success) {
      return validationResult;
    }

    const source = CancelToken.source();
    this.activeUploads.set(file.name, source);

    try {
      const preparation = await axiosInstance.post('/api/files/upload-url', {
        filename: file.name,
        contentType: file.type,
        size: file.size,
      }, {
        cancelToken: source.token,
      });

      let response;
      if (preparation.data?.directUpload) {
        const pendingFile = preparation.data.file;
        await this.putToPresignedUrl(
          preparation.data.uploadUrl,
          file,
          preparation.data.requiredHeaders,
          source.token,
          onProgress
        );
        response = await axiosInstance.post(
          `/api/files/uploads/${encodeURIComponent(pendingFile._id)}/complete`,
          {},
          { cancelToken: source.token }
        );
      } else {
        response = await this.uploadThroughBackend(file, source.token, onProgress);
      }

      this.activeUploads.delete(file.name);

      if (!response.data || !response.data.success) {
        return {
          success: false,
          message: response.data?.message || '파일 업로드에 실패했습니다.'
        };
      }

      const fileData = response.data.file;
      return {
        success: true,
        data: {
          ...response.data,
          file: {
            ...fileData,
            url: fileData.url || this.getFileUrl(fileData.filename, true)
          }
        }
      };

    } catch (error) {
      this.activeUploads.delete(file.name);

      if (isCancel(error)) {
        return {
          success: false,
          message: '업로드가 취소되었습니다.'
        };
      }

      if (error.response?.status === 401) {
        throw new Error('Authentication expired. Please login again.');
      }

      return this.handleUploadError(error);
    }
  }

  async uploadThroughBackend(file, cancelToken, onProgress) {
    const formData = new FormData();
    formData.append('file', file);

    return axiosInstance.post('/api/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000,
      cancelToken,
      withCredentials: true,
      onUploadProgress: (progressEvent) => {
        if (onProgress) {
          const total = progressEvent.total || file.size;
          onProgress(Math.round((progressEvent.loaded * 100) / total));
        }
      }
    });
  }

  async putToPresignedUrl(uploadUrl, file, requiredHeaders, cancelToken, onProgress) {
    return axios.put(uploadUrl, file, {
      headers: requiredHeaders || { 'Content-Type': file.type },
      timeout: 30000,
      cancelToken,
      onUploadProgress: (progressEvent) => {
        if (onProgress) {
          const total = progressEvent.total || file.size;
          onProgress(Math.round((progressEvent.loaded * 100) / total));
        }
      }
    });
  }

  async uploadProfileImage(file, onProgress) {
    const preparation = await axiosInstance.post('/api/users/profile-image/upload-url', {
      filename: file.name,
      contentType: file.type,
      size: file.size,
    });

    if (!preparation.data?.directUpload) {
      const formData = new FormData();
      formData.append('profileImage', file);
      const response = await axiosInstance.post('/api/users/profile-image', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 30000,
        onUploadProgress: (progressEvent) => {
          if (onProgress) {
            const total = progressEvent.total || file.size;
            onProgress(Math.round((progressEvent.loaded * 100) / total));
          }
        }
      });
      return response.data;
    }

    await this.putToPresignedUrl(
      preparation.data.uploadUrl,
      file,
      preparation.data.requiredHeaders,
      undefined,
      onProgress
    );
    const response = await axiosInstance.post('/api/users/profile-image/complete', {
      objectKey: preparation.data.objectKey,
    });
    return response.data;
  }

  getFileUrl(fileOrFilename, forPreview = false) {
    const directUrl = typeof fileOrFilename === 'object' ? fileOrFilename?.url : '';
    if (directUrl) {
      if (/^https?:\/\//i.test(directUrl)) return directUrl;

      const apiBaseUrl = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '');
      const normalizedPath = directUrl.startsWith('/') ? directUrl : `/${directUrl}`;
      return `${apiBaseUrl}${normalizedPath}`;
    }

    const filename = typeof fileOrFilename === 'object' ? fileOrFilename?.filename : fileOrFilename;
    if (!filename) return '';

    const baseUrl = process.env.NEXT_PUBLIC_API_URL || '';
    const endpoint = forPreview ? 'view' : 'download';
    return `${baseUrl}/api/files/${endpoint}/${filename}`;
  }

  getPreviewUrl(file, token, sessionId, withAuth = true) {
    if (!file?.filename) return '';

    const baseUrl = this.getFileUrl(file, true);

    // 절대 URL은 백엔드가 발급한 공개 CDN 주소다. 상대 URL은 인증이 필요한 백엔드 프록시다.
    if (/^https?:\/\//i.test(file.url || '')) return baseUrl;

    if (!withAuth) return baseUrl;

    if (!token || !sessionId) return baseUrl;

    // URL 객체 생성 전 프로토콜 확인
    const url = new URL(baseUrl, globalThis.location?.origin || 'http://localhost');
    url.searchParams.append('token', encodeURIComponent(token));
    url.searchParams.append('sessionId', encodeURIComponent(sessionId));

    return url.toString();
  }

  getFileExtension(filename) {
    if (!filename) return '';
    const parts = filename.split('.');
    return parts.length > 1 ? `.${parts.pop().toLowerCase()}` : '';
  }

  formatFileSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${parseFloat((bytes / Math.pow(1024, i)).toFixed(2))} ${units[i]}`;
  }

  handleUploadError(error) {
    if (error.code === 'ECONNABORTED') {
      return {
        success: false,
        message: '파일 업로드 시간이 초과되었습니다.'
      };
    }

    const status = error.response?.status ?? error.status;
    const message = error.response?.data?.message ?? error.message;

    switch (status) {
      case 400:
        return {
          success: false,
          message: message || '잘못된 요청입니다.'
        };
      case 401:
        return {
          success: false,
          message: '인증이 필요합니다.'
        };
      case 413:
        return {
          success: false,
          message: message || '파일이 너무 큽니다.'
        };
      case 415:
        return {
          success: false,
          message: '지원하지 않는 파일 형식입니다.'
        };
      default:
        break;
    }

    console.error('Upload error:', error);

    if (axios.isAxiosError(error)) {
      switch (status) {
        case 500:
          return {
            success: false,
            message: '서버 오류가 발생했습니다.'
          };
        default:
          return {
            success: false,
            message: message || '파일 업로드에 실패했습니다.'
          };
      }
    }

    return {
      success: false,
      message: error.message || '알 수 없는 오류가 발생했습니다.',
      error
    };
  }

  cancelUpload(filename) {
    const source = this.activeUploads.get(filename);
    if (source) {
      source.cancel('Upload canceled by user');
      this.activeUploads.delete(filename);
      return {
        success: true,
        message: '업로드가 취소되었습니다.'
      };
    }
    return {
      success: false,
      message: '취소할 업로드를 찾을 수 없습니다.'
    };
  }

}

export default new FileService();
