import React, { useState } from 'react';
import { useRouter } from 'next/router';
import { useAuth, withoutAuth } from '@/contexts/AuthContext';
import useIsHydrated from '@/hooks/useIsHydrated';
import styles from '../styles/Register.module.css';

const Register = () => {
  const isHydrated = useIsHydrated();
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const router = useRouter();
  const { register: registerContext } = useAuth();
  const interactionDisabled = !isHydrated || loading;

  const validateForm = () => {
    // 비밀번호 일치 확인만 추가 검증 (나머지는 HTML5 폼 검증)
    if (formData.password !== formData.confirmPassword) {
      setError('비밀번호가 일치하지 않습니다.');
      return false;
    }

    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(false);

    try {
      const { name, email, password } = formData;
      await registerContext({ name, email, password });
      
      setSuccess(true);
      setLoading(false);
      
      setTimeout(() => {
        router.push('/');
      }, 1000);
    } catch (err) {
      setError(err.message || '회원가입 처리 중 오류가 발생했습니다.');
      setLoading(false);
    }
  };

  return (
    <main
      className={styles.page}
      data-testid="register-page"
      data-hydrated={isHydrated ? 'true' : 'false'}
      aria-busy={interactionDisabled}
    >
      <form
        className={styles.form}
        onSubmit={handleSubmit}
      >
        <img
          src="/images/logo-h.png"
          width="439"
          height="220"
          fetchPriority="high"
          className={styles.logo}
          alt="KTB Chat 로고"
        />

        {error && (
          <div
            className={`${styles.callout} ${styles.warning}`}
            role="alert"
            data-testid="register-error-message"
          >
            {error}
          </div>
        )}

        {success && (
          <div
            className={`${styles.callout} ${styles.success}`}
            role="status"
            data-testid="register-success-message"
          >
            가입성공, 로그인 해 주세요.
          </div>
        )}

        <div className={styles.actions}>
          <div className={styles.fields}>
            <label className={styles.field} htmlFor="register-name">
              <span className={styles.label}>이름</span>
              <input
                className={styles.input}
                id="register-name"
                name="name"
                type="text"
                required
                disabled={interactionDisabled}
                value={formData.name}
                onChange={(event) => setFormData(prev => ({ ...prev, name: event.target.value }))}
                placeholder="이름을 입력하세요"
                autoComplete="name"
                data-testid="register-name-input"
              />
            </label>

            <label className={styles.field} htmlFor="register-email">
              <span className={styles.label}>이메일</span>
              <input
                className={styles.input}
                id="register-email"
                name="email"
                type="email"
                required
                disabled={interactionDisabled}
                value={formData.email}
                onChange={(event) => setFormData(prev => ({ ...prev, email: event.target.value }))}
                placeholder="이메일을 입력하세요"
                autoComplete="email"
                data-testid="register-email-input"
              />
            </label>

            <label className={styles.field} htmlFor="register-password">
              <span className={styles.label}>비밀번호</span>
              <input
                className={styles.input}
                id="register-password"
                name="password"
                type="password"
                required
                disabled={interactionDisabled}
                value={formData.password}
                onChange={(event) => setFormData(prev => ({ ...prev, password: event.target.value }))}
                placeholder="비밀번호를 입력하세요"
                pattern="(?=.*\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[\W_]).{8,16}"
                autoComplete="new-password"
                aria-describedby="register-password-description"
                data-testid="register-password-input"
              />
              <p id="register-password-description" className={styles.description}>
                8~16자, 대소문자 영문, 숫자, 특수문자 포함
              </p>
            </label>

            <label className={styles.field} htmlFor="register-password-confirm">
              <span className={styles.label}>비밀번호 확인</span>
              <input
                className={styles.input}
                id="register-password-confirm"
                name="confirmPassword"
                type="password"
                required
                disabled={interactionDisabled}
                value={formData.confirmPassword}
                onChange={(event) => setFormData(prev => ({ ...prev, confirmPassword: event.target.value }))}
                placeholder="비밀번호를 다시 입력하세요"
                autoComplete="new-password"
                data-testid="register-password-confirm-input"
              />
            </label>
          </div>

          <button
            className={`${styles.button} ${styles.submitButton}`}
            type="submit"
            disabled={interactionDisabled}
            data-testid="register-submit-button"
          >
            {loading ? '회원가입 중...' : '회원가입'}
          </button>
        </div>

        <div className={styles.footer}>
          <span>이미 계정이 있으신가요?</span>
          <button
            className={`${styles.button} ${styles.linkButton}`}
            type="button"
            onClick={() => router.push('/')}
            disabled={interactionDisabled}
          >
            로그인
          </button>
        </div>
      </form>
    </main>
  );
};

export default withoutAuth(Register);
