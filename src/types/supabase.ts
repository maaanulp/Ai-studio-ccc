export interface SupabaseConfig {
  supabaseUrl: string;
  supabaseAnonKey: string;
  redirectUrl: string;
}

export interface OperativeProfile {
  id: string;
  email: string;
  handle: string;
  role: 'OPERATIVE' | 'ADMIN';
  crewId: string;
  avatarUrl?: string;
  lastActive: number;
}

export interface GoogleOAuthOptions {
  provider: 'google';
  options: {
    redirectTo: string;
    queryParams?: {
      access_type?: string;
      prompt?: string;
    };
  };
}
