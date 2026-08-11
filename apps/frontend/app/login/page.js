import { redirect } from 'next/navigation';

const appendSearchParam = (params, key, value) => {
  if (Array.isArray(value)) {
    value.forEach((item) => params.append(key, item));
  } else if (value !== undefined) {
    params.set(key, value);
  }
};

export default async function LoginRedirectPage({ searchParams }) {
  const incomingParams = await searchParams;
  const params = new URLSearchParams();

  Object.entries(incomingParams || {}).forEach(([key, value]) => {
    appendSearchParam(params, key, value);
  });

  redirect(params.size > 0 ? `/?${params.toString()}` : '/');
}
