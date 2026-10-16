import { MessageCircle } from "lucide-react";
import { motion } from "framer-motion";

export function WhatsAppFab() {
  return (
    <motion.a
      href="https://wa.me/919876543210?text=Hi%20Mini%27s%20n%20More%2C%20I%20have%20a%20question"
      target="_blank" rel="noreferrer"
      initial={{ scale: 0, rotate: -45 }} animate={{ scale: 1, rotate: 0 }}
      transition={{ delay: 1, type: "spring", stiffness: 200 }}
      whileHover={{ scale: 1.08 }}
      className="fixed bottom-5 right-5 z-40 h-14 w-14 grid place-items-center rounded-full text-white shadow-glow"
      style={{ background: "linear-gradient(135deg, #25D366, #128C7E)" }}
      aria-label="Chat on WhatsApp">
      <MessageCircle className="h-6 w-6" />
      <span className="absolute inset-0 rounded-full animate-ping opacity-30" style={{ background: "#25D366" }} />
    </motion.a>
  );
}
