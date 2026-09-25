# Achei a Loja

App Android para motoboys: escolha o shopping e veja o mapa interno com as lojas de comida, a entrada de motoboys e o balcão de retirada.

- **Login** com e-mail e senha (Firebase Authentication)
- **Mapas online** (Cloud Firestore): você edita e todos recebem na hora; depois de aberto uma vez, funciona sem internet
- Busca de lojas por nome, número ou categoria; zoom e arrastar; vários pisos
- Favoritos sincronizados, botão "Algo errado no mapa?" e **Excluir conta** (exigido pela Play Store)
- Shopping de exemplo dentro do app para testar

Os mapas são desenhados no **Editor de mapas** (a página do Achei a Loja no Claude): carregue a planta, decalque as lojas, copie o JSON e cole no Firebase.

---

## Passo a passo até a Play Store

### 1. Criar o Firebase (grátis) — 10 min
1. Acesse <https://console.firebase.google.com> → **Adicionar projeto** → nome `achei-a-loja`.
2. **Authentication** → Começar → ative **E-mail/senha**.
3. **Firestore Database** → Criar banco → região `southamerica-east1` (São Paulo) → modo produção.
4. Na aba **Regras** do Firestore, cole o conteúdo do arquivo `firestore.rules` deste projeto e clique **Publicar**.
5. Em ⚙️ **Configurações do projeto** → **Adicionar app** → Android → nome do pacote: `com.acheialoja.app` → baixe o **google-services.json**.

### 2. Cadastrar o primeiro shopping
1. Abra o Editor de mapas, desenhe (ou use o exemplo) e clique **Copiar JSON**.
2. No Firestore: **Iniciar coleção** `shoppings` → ID do documento (ex.: `shopping-centro`) → campos:
   - `nome` (string), `cidade` (string), `ativo` (boolean = true)
   - `mapa` (string) → cole o JSON

### 3. Gerar o arquivo do app (.aab)

**Opção A — GitHub (não precisa instalar nada)**
1. Crie uma conta em <https://github.com> e um repositório **público** chamado `AcheiALoja`.
2. Envie todos os arquivos desta pasta (botão **Add file → Upload files**; arraste a pasta inteira, inclusive `.github`).
   Pastas que começam com ponto costumam ficar ocultas e podem não subir. Se `.github` não aparecer no repositório, use **Add file → Create new file**, digite o nome `.github/workflows/gerar-app.yml` e cole o conteúdo desse arquivo.
3. Em **Settings → Secrets and variables → Actions**, crie os segredos do arquivo `LEIA-GUARDE-EM-LOCAL-SEGURO.txt`:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` e `GOOGLE_SERVICES_JSON` (conteúdo do google-services.json).
4. Aba **Actions** → **Gerar app para a Play Store** → **Run workflow**. Em ~8 min, baixe o resultado em **Artifacts**:
   o `.aab` (para a Play Store) e o `.apk` (para instalar direto no seu celular e testar).

**Opção B — Android Studio**
1. Instale o Android Studio e abra esta pasta.
2. Copie o `google-services.json` para a pasta `app/`.
3. Menu **Build → Generate Signed App Bundle / APK** → Android App Bundle → use o `upload-keystore.jks` e a senha do arquivo de segredos.

### 4. Política de privacidade e exclusão de conta
A Play Store exige links públicos. Estão prontos na pasta `docs/`:
1. Troque `SEU-EMAIL@exemplo.com` pelo seu e-mail nos dois arquivos.
2. No GitHub: **Settings → Pages** → Branch `main`, pasta `/docs` → Save.
3. Os links ficam: `https://SEU-USUARIO.github.io/AcheiALoja/politica-privacidade.html` e `.../excluir-conta.html`.
4. Coloque o primeiro link em `app/src/main/res/values/strings.xml` (`url_privacidade`) e gere o app de novo.

### 5. Publicar no Google Play Console
1. Crie a conta de desenvolvedor em <https://play.google.com/console> (taxa única de US$ 25).
2. **Criar app** → nome "Achei a Loja", idioma Português (Brasil), App, Gratuito.
3. Preencha **Conteúdo do app**:
   - Política de privacidade: link do passo 4
   - **Acesso ao app**: crie uma conta de teste no app e informe e-mail/senha (o revisor do Google precisa entrar)
   - Anúncios: não · Classificação etária: questionário · Público-alvo: 18+
   - **Segurança dos dados**: coleta *E-mail* e *Outro conteúdo gerado pelo usuário* (sugestões); criptografado em trânsito; usuário pode pedir exclusão (link `excluir-conta.html`)
4. **Página da loja**: ícone `icone-512.png`, imagem de destaque `destaque-1024x500.png` e pelo menos 2 capturas de tela do celular.
5. **Teste fechado**: contas pessoais novas precisam de **12 testadores por 14 dias** antes de liberar a produção. Suba o `.aab`, convide 12 pessoas (motoboys conhecidos são ótimos!) e peça que mantenham o app instalado.
6. Depois dos 14 dias: **Solicitar acesso à produção** → enviar para revisão.

### Atualizar o app
Mapas: só editar no Firebase (não precisa publicar nada).
Código: envie a alteração ao GitHub → o Actions gera um novo `.aab` com versão maior → suba no Play Console.

---

## Estrutura
```
app/src/main/java/com/acheialoja/app/
  MainActivity.kt          navegação entre telas
  data/Modelos.kt          formato do mapa (JSON) e leitura
  data/Repositorio.kt      Firebase: login, shoppings, favoritos, sugestões, excluir conta
  ui/TelaLogin.kt          entrar / cadastrar / recuperar senha
  ui/TelaShoppings.kt      lista e busca de shoppings
  ui/TelaMapa.kt           busca de loja, pisos, painel de informações
  ui/MapaInterativo.kt     desenho do mapa com zoom e toque
  ui/TelaConta.kt          sair, privacidade, excluir conta
app/src/main/assets/exemplo.json   shopping de exemplo
firestore.rules                    regras de segurança do banco
docs/                              política de privacidade e exclusão de conta
```

## Formato do mapa
Coordenadas em unidades de desenho (padrão 1000 × 700).
```json
{ "id": "shopping-centro", "nome": "...", "cidade": "...", "observacoes": "Aviso para motoboys",
  "largura": 1000, "altura": 700,
  "pisos": [{ "id": "T", "nome": "Térreo",
    "formas": [{ "tipo": "contorno|corredor|praca|bloqueado", "x": 0, "y": 0, "w": 100, "h": 50, "rotulo": "" }],
    "lojas":  [{ "id": "l1", "nome": "Burger", "categoria": "lanches", "numero": "L-12", "dica": "", "x": 0, "y": 0, "w": 100, "h": 80 }],
    "pontos": [{ "tipo": "entrada_motoboy|estacionamento|retirada|entrada|escada|elevador|banheiro", "x": 30, "y": 340, "rotulo": "" }]
  }]
}
```
