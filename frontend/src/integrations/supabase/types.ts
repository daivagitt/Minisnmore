export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "14.5"
  }
  public: {
    Tables: {
      banners: {
        Row: {
          active: boolean
          created_at: string
          cta_label: string | null
          id: string
          image_url: string | null
          link_url: string | null
          sort_order: number
          subtitle: string | null
          title: string
          updated_at: string
        }
        Insert: {
          active?: boolean
          created_at?: string
          cta_label?: string | null
          id?: string
          image_url?: string | null
          link_url?: string | null
          sort_order?: number
          subtitle?: string | null
          title: string
          updated_at?: string
        }
        Update: {
          active?: boolean
          created_at?: string
          cta_label?: string | null
          id?: string
          image_url?: string | null
          link_url?: string | null
          sort_order?: number
          subtitle?: string | null
          title?: string
          updated_at?: string
        }
        Relationships: []
      }
      blog_posts: {
        Row: {
          content: string | null
          cover_url: string | null
          created_at: string
          excerpt: string | null
          id: string
          published: boolean
          published_at: string | null
          slug: string
          title: string
          updated_at: string
        }
        Insert: {
          content?: string | null
          cover_url?: string | null
          created_at?: string
          excerpt?: string | null
          id?: string
          published?: boolean
          published_at?: string | null
          slug: string
          title: string
          updated_at?: string
        }
        Update: {
          content?: string | null
          cover_url?: string | null
          created_at?: string
          excerpt?: string | null
          id?: string
          published?: boolean
          published_at?: string | null
          slug?: string
          title?: string
          updated_at?: string
        }
        Relationships: []
      }
      categories: {
        Row: {
          active: boolean
          created_at: string
          description: string | null
          id: string
          image_url: string | null
          name: string
          slug: string
          sort_order: number
          updated_at: string
        }
        Insert: {
          active?: boolean
          created_at?: string
          description?: string | null
          id?: string
          image_url?: string | null
          name: string
          slug: string
          sort_order?: number
          updated_at?: string
        }
        Update: {
          active?: boolean
          created_at?: string
          description?: string | null
          id?: string
          image_url?: string | null
          name?: string
          slug?: string
          sort_order?: number
          updated_at?: string
        }
        Relationships: []
      }
      coupons: {
        Row: {
          active: boolean
          code: string
          created_at: string
          discount_type: string
          discount_value: number
          expires_at: string | null
          id: string
          max_discount: number | null
          min_order: number
          updated_at: string
        }
        Insert: {
          active?: boolean
          code: string
          created_at?: string
          discount_type?: string
          discount_value?: number
          expires_at?: string | null
          id?: string
          max_discount?: number | null
          min_order?: number
          updated_at?: string
        }
        Update: {
          active?: boolean
          code?: string
          created_at?: string
          discount_type?: string
          discount_value?: number
          expires_at?: string | null
          id?: string
          max_discount?: number | null
          min_order?: number
          updated_at?: string
        }
        Relationships: []
      }
      home_sections: {
        Row: {
          enabled: boolean
          id: string
          key: string
          payload: Json
          sort_order: number
          subtitle: string | null
          title: string | null
          updated_at: string
        }
        Insert: {
          enabled?: boolean
          id?: string
          key: string
          payload?: Json
          sort_order?: number
          subtitle?: string | null
          title?: string | null
          updated_at?: string
        }
        Update: {
          enabled?: boolean
          id?: string
          key?: string
          payload?: Json
          sort_order?: number
          subtitle?: string | null
          title?: string | null
          updated_at?: string
        }
        Relationships: []
      }
      order_items: {
        Row: {
          color: string | null
          created_at: string
          id: string
          order_id: string
          price: number
          product_id: string
          product_image: string | null
          product_name: string
          product_slug: string | null
          qty: number
          size: string | null
        }
        Insert: {
          color?: string | null
          created_at?: string
          id?: string
          order_id: string
          price?: number
          product_id: string
          product_image?: string | null
          product_name: string
          product_slug?: string | null
          qty?: number
          size?: string | null
        }
        Update: {
          color?: string | null
          created_at?: string
          id?: string
          order_id?: string
          price?: number
          product_id?: string
          product_image?: string | null
          product_name?: string
          product_slug?: string | null
          qty?: number
          size?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "order_items_order_id_fkey"
            columns: ["order_id"]
            isOneToOne: false
            referencedRelation: "orders"
            referencedColumns: ["id"]
          },
        ]
      }
      orders: {
        Row: {
          coupon_code: string | null
          created_at: string
          discount: number
          donation: number
          id: string
          notes: string | null
          order_number: string
          payment_status: string
          razorpay_order_id: string | null
          razorpay_payment_id: string | null
          razorpay_signature: string | null
          shipping: number
          shipping_address: Json
          status: string
          subtotal: number
          tax: number
          total: number
          updated_at: string
          user_id: string
        }
        Insert: {
          coupon_code?: string | null
          created_at?: string
          discount?: number
          donation?: number
          id?: string
          notes?: string | null
          order_number: string
          payment_status?: string
          razorpay_order_id?: string | null
          razorpay_payment_id?: string | null
          razorpay_signature?: string | null
          shipping?: number
          shipping_address?: Json
          status?: string
          subtotal?: number
          tax?: number
          total?: number
          updated_at?: string
          user_id: string
        }
        Update: {
          coupon_code?: string | null
          created_at?: string
          discount?: number
          donation?: number
          id?: string
          notes?: string | null
          order_number?: string
          payment_status?: string
          razorpay_order_id?: string | null
          razorpay_payment_id?: string | null
          razorpay_signature?: string | null
          shipping?: number
          shipping_address?: Json
          status?: string
          subtotal?: number
          tax?: number
          total?: number
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      page_visibility: {
        Row: {
          id: string
          page_key: string
          updated_at: string
          visible: boolean
        }
        Insert: {
          id?: string
          page_key: string
          updated_at?: string
          visible?: boolean
        }
        Update: {
          id?: string
          page_key?: string
          updated_at?: string
          visible?: boolean
        }
        Relationships: []
      }
      policies: {
        Row: {
          body: string | null
          created_at: string
          id: string
          slug: string
          title: string
          updated_at: string
        }
        Insert: {
          body?: string | null
          created_at?: string
          id?: string
          slug: string
          title: string
          updated_at?: string
        }
        Update: {
          body?: string | null
          created_at?: string
          id?: string
          slug?: string
          title?: string
          updated_at?: string
        }
        Relationships: []
      }
      product_overrides: {
        Row: {
          badge: string | null
          created_at: string
          featured: boolean
          gallery: Json
          id: string
          slug: string
          story: string | null
          updated_at: string
          zoho_item_id: string | null
        }
        Insert: {
          badge?: string | null
          created_at?: string
          featured?: boolean
          gallery?: Json
          id?: string
          slug: string
          story?: string | null
          updated_at?: string
          zoho_item_id?: string | null
        }
        Update: {
          badge?: string | null
          created_at?: string
          featured?: boolean
          gallery?: Json
          id?: string
          slug?: string
          story?: string | null
          updated_at?: string
          zoho_item_id?: string | null
        }
        Relationships: []
      }
      products: {
        Row: {
          active: boolean
          age_max_months: number | null
          age_min_months: number | null
          badge: string | null
          best_seller: boolean
          category_id: string | null
          colors: Json
          compare_at_price: number | null
          created_at: string
          currency: string
          description: string | null
          fabric_type: string | null
          featured: boolean
          id: string
          images: Json
          name: string
          new_arrival: boolean
          price: number
          sizes: Json
          sku: string | null
          slug: string
          sort_order: number
          stock: number
          story: string | null
          trending: boolean
          updated_at: string
        }
        Insert: {
          active?: boolean
          age_max_months?: number | null
          age_min_months?: number | null
          badge?: string | null
          best_seller?: boolean
          category_id?: string | null
          colors?: Json
          compare_at_price?: number | null
          created_at?: string
          currency?: string
          description?: string | null
          fabric_type?: string | null
          featured?: boolean
          id?: string
          images?: Json
          name: string
          new_arrival?: boolean
          price?: number
          sizes?: Json
          sku?: string | null
          slug: string
          sort_order?: number
          stock?: number
          story?: string | null
          trending?: boolean
          updated_at?: string
        }
        Update: {
          active?: boolean
          age_max_months?: number | null
          age_min_months?: number | null
          badge?: string | null
          best_seller?: boolean
          category_id?: string | null
          colors?: Json
          compare_at_price?: number | null
          created_at?: string
          currency?: string
          description?: string | null
          fabric_type?: string | null
          featured?: boolean
          id?: string
          images?: Json
          name?: string
          new_arrival?: boolean
          price?: number
          sizes?: Json
          sku?: string | null
          slug?: string
          sort_order?: number
          stock?: number
          story?: string | null
          trending?: boolean
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "products_category_id_fkey"
            columns: ["category_id"]
            isOneToOne: false
            referencedRelation: "categories"
            referencedColumns: ["id"]
          },
        ]
      }
      profiles: {
        Row: {
          created_at: string
          full_name: string | null
          id: string
          phone: string | null
          updated_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          full_name?: string | null
          id?: string
          phone?: string | null
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          full_name?: string | null
          id?: string
          phone?: string | null
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      site_settings: {
        Row: {
          brand_name: string
          care_label: string | null
          contact_email: string | null
          contact_phone: string | null
          ecatalogue_label: string | null
          ecatalogue_url: string | null
          facebook_url: string | null
          footer_copyright: string | null
          footer_tagline: string | null
          id: number
          instagram_url: string | null
          legal_entity_name: string | null
          logo_horizontal_url: string | null
          logo_url: string | null
          twitter_url: string | null
          updated_at: string
          whatsapp_number: string | null
          youtube_url: string | null
        }
        Insert: {
          brand_name?: string
          care_label?: string | null
          contact_email?: string | null
          contact_phone?: string | null
          ecatalogue_label?: string | null
          ecatalogue_url?: string | null
          facebook_url?: string | null
          footer_copyright?: string | null
          footer_tagline?: string | null
          id?: number
          instagram_url?: string | null
          legal_entity_name?: string | null
          logo_horizontal_url?: string | null
          logo_url?: string | null
          twitter_url?: string | null
          updated_at?: string
          whatsapp_number?: string | null
          youtube_url?: string | null
        }
        Update: {
          brand_name?: string
          care_label?: string | null
          contact_email?: string | null
          contact_phone?: string | null
          ecatalogue_label?: string | null
          ecatalogue_url?: string | null
          facebook_url?: string | null
          footer_copyright?: string | null
          footer_tagline?: string | null
          id?: number
          instagram_url?: string | null
          legal_entity_name?: string | null
          logo_horizontal_url?: string | null
          logo_url?: string | null
          twitter_url?: string | null
          updated_at?: string
          whatsapp_number?: string | null
          youtube_url?: string | null
        }
        Relationships: []
      }
      user_roles: {
        Row: {
          created_at: string
          id: string
          role: Database["public"]["Enums"]["app_role"]
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          role?: Database["public"]["Enums"]["app_role"]
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          role?: Database["public"]["Enums"]["app_role"]
          user_id?: string
        }
        Relationships: []
      }
      wishlist_items: {
        Row: {
          created_at: string
          id: string
          product_id: string
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          product_id: string
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          product_id?: string
          user_id?: string
        }
        Relationships: []
      }
      zoho_settings: {
        Row: {
          client_id: string | null
          client_secret: string | null
          connected: boolean
          data_center: string | null
          id: number
          org_id: string | null
          refresh_token: string | null
          updated_at: string
        }
        Insert: {
          client_id?: string | null
          client_secret?: string | null
          connected?: boolean
          data_center?: string | null
          id?: number
          org_id?: string | null
          refresh_token?: string | null
          updated_at?: string
        }
        Update: {
          client_id?: string | null
          client_secret?: string | null
          connected?: boolean
          data_center?: string | null
          id?: number
          org_id?: string | null
          refresh_token?: string | null
          updated_at?: string
        }
        Relationships: []
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      has_role: {
        Args: {
          _role: Database["public"]["Enums"]["app_role"]
          _user_id: string
        }
        Returns: boolean
      }
    }
    Enums: {
      app_role: "admin" | "editor" | "user"
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {
      app_role: ["admin", "editor", "user"],
    },
  },
} as const
